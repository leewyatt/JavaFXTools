package io.github.leewyatt.fxtools.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms SVG path data strings: precision simplification and coordinate normalization.
 *
 * <p>Both operations parse the path into a structured command list, transform
 * the numeric parameters, and serialize back to a string. The original command
 * types (absolute/relative, H/V/S/T/etc.) are preserved.</p>
 */
public final class SvgPathTransformer {

    private static final Pattern CMD_PATTERN =
            Pattern.compile("([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)");
    private static final Pattern NUM_PATTERN =
            Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    private SvgPathTransformer() {
    }

    // ==================== Public API ====================

    /**
     * Truncates coordinate values to the specified number of decimal places.
     * Arc flags and rotation are left as-is.
     *
     * @param pathData      the SVG path data string
     * @param decimalPlaces number of decimal places to keep (0 = integers only)
     * @return simplified path data, or {@code null} if parsing fails
     */
    @Nullable
    public static String simplifyPrecision(@NotNull String pathData, int decimalPlaces) {
        List<PathCommand> commands = parse(pathData);
        if (commands.isEmpty()) {
            return null;
        }
        return serialize(commands, Math.max(0, decimalPlaces));
    }

    /**
     * Normalizes path coordinates to fit within a {@code targetSize × targetSize} box
     * starting at the origin (0, 0).
     *
     * <p>The path is uniformly scaled (preserving aspect ratio) and translated so that
     * its bounding box fits within the target size. Arc radii are scaled accordingly;
     * arc flags and rotation are preserved.</p>
     *
     * @param pathData   the SVG path data string
     * @param targetSize the target width/height (e.g. 24 for a 24×24 icon)
     * @param precision  decimal places for the output coordinates
     * @return normalized path data, or {@code null} if parsing fails or path is empty
     */
    @Nullable
    public static String normalize(@NotNull String pathData, double targetSize, int precision) {
        List<PathCommand> commands = parse(pathData);
        if (commands.isEmpty()) {
            return null;
        }

        // Use FxSvgRenderer to get the bounding box (it handles all command types)
        GeneralPath generalPath = FxSvgRenderer.parseSvgPath(pathData);
        if (generalPath == null) {
            return null;
        }
        Rectangle2D bounds = generalPath.getBounds2D();
        if (bounds.getWidth() <= 0 && bounds.getHeight() <= 0) {
            return null;
        }

        double maxDim = Math.max(bounds.getWidth(), bounds.getHeight());
        if (maxDim <= 0) {
            return null;
        }
        double scale = targetSize / maxDim;
        double tx = -bounds.getX();
        double ty = -bounds.getY();
        // Center the shorter axis
        double offsetX = (targetSize - bounds.getWidth() * scale) / 2;
        double offsetY = (targetSize - bounds.getHeight() * scale) / 2;

        List<PathCommand> transformed = transformCommands(commands, scale, tx, ty, offsetX, offsetY);
        return serialize(transformed, Math.max(0, precision));
    }

    // ==================== Structured Path Parsing ====================

    /**
     * A single SVG path command with its type and numeric parameters.
     * The type preserves case (uppercase = absolute, lowercase = relative).
     */
    private record PathCommand(char type, double[] params) {
    }

    /**
     * Parameter role for each position in a command's parameter list.
     */
    private enum ParamRole {
        /** X-coordinate: translates and scales for absolute, scales only for relative. */
        X,
        /** Y-coordinate: translates and scales for absolute, scales only for relative. */
        Y,
        /** Radius or distance: scales but never translates. */
        SCALE_ONLY,
        /** Flag or angle: never modified by normalization (only affected by precision). */
        KEEP
    }

    /**
     * Returns the parameter roles for one repeat of the given command.
     * For example, 'C' returns [X, Y, X, Y, X, Y] (6 params per repeat).
     */
    @NotNull
    private static ParamRole[] getParamRoles(char upperCmd) {
        return switch (upperCmd) {
            case 'M', 'L', 'T' -> new ParamRole[]{ParamRole.X, ParamRole.Y};
            case 'H' -> new ParamRole[]{ParamRole.X};
            case 'V' -> new ParamRole[]{ParamRole.Y};
            case 'C' -> new ParamRole[]{ParamRole.X, ParamRole.Y, ParamRole.X, ParamRole.Y, ParamRole.X, ParamRole.Y};
            case 'S', 'Q' -> new ParamRole[]{ParamRole.X, ParamRole.Y, ParamRole.X, ParamRole.Y};
            case 'A' -> new ParamRole[]{ParamRole.SCALE_ONLY, ParamRole.SCALE_ONLY, ParamRole.KEEP, ParamRole.KEEP, ParamRole.KEEP, ParamRole.X, ParamRole.Y};
            default -> new ParamRole[0]; // Z or unknown
        };
    }

    /**
     * Parses SVG path data into a list of structured commands.
     * Handles implicit repeated parameters (e.g. {@code M 10,10 20,20}
     * becomes M(10,10) + implicit L(20,20)).
     */
    @NotNull
    private static List<PathCommand> parse(@NotNull String pathData) {
        List<PathCommand> commands = new ArrayList<>();
        Matcher cmdMatcher = CMD_PATTERN.matcher(pathData.trim());

        while (cmdMatcher.find()) {
            char type = cmdMatcher.group(1).charAt(0);
            String paramStr = cmdMatcher.group(2);
            List<Double> nums = parseNumbers(paramStr);
            char upper = Character.toUpperCase(type);

            if (upper == 'Z') {
                commands.add(new PathCommand(type, new double[0]));
                continue;
            }

            ParamRole[] roles = getParamRoles(upper);
            if (roles.length == 0) {
                continue;
            }

            // Split params into groups of the expected size
            int groupSize = roles.length;
            if (nums.isEmpty()) {
                // Command with no params (shouldn't happen except Z, but be safe)
                commands.add(new PathCommand(type, new double[0]));
                continue;
            }

            for (int i = 0; i + groupSize - 1 < nums.size(); i += groupSize) {
                double[] params = new double[groupSize];
                for (int j = 0; j < groupSize; j++) {
                    params[j] = nums.get(i + j);
                }
                if (i == 0) {
                    commands.add(new PathCommand(type, params));
                } else {
                    // Implicit repeated command: after M → implicit L, after m → implicit l
                    char implicitType = type;
                    if (upper == 'M') {
                        implicitType = Character.isUpperCase(type) ? 'L' : 'l';
                    }
                    commands.add(new PathCommand(implicitType, params));
                }
            }
        }
        return commands;
    }

    // ==================== Transform ====================

    /**
     * Applies translation and uniform scaling to all commands.
     *
     * @param commands the parsed commands
     * @param scale    uniform scale factor
     * @param tx       x-translation applied BEFORE scaling (shifts origin)
     * @param ty       y-translation applied BEFORE scaling (shifts origin)
     * @param offsetX  x-offset applied AFTER scaling (centering)
     * @param offsetY  y-offset applied AFTER scaling (centering)
     */
    @NotNull
    private static List<PathCommand> transformCommands(@NotNull List<PathCommand> commands,
                                                       double scale,
                                                       double tx, double ty,
                                                       double offsetX, double offsetY) {
        List<PathCommand> result = new ArrayList<>(commands.size());
        for (int ci = 0; ci < commands.size(); ci++) {
            PathCommand cmd = commands.get(ci);
            char upper = Character.toUpperCase(cmd.type);
            // SVG spec: a leading lowercase 'm' is relative to (0,0), i.e. absolute
            boolean relative = Character.isLowerCase(cmd.type) && !(ci == 0 && cmd.type == 'm');
            ParamRole[] roles = getParamRoles(upper);
            double[] newParams = new double[cmd.params.length];

            for (int i = 0; i < cmd.params.length; i++) {
                ParamRole role = i < roles.length ? roles[i] : ParamRole.KEEP;
                double v = cmd.params[i];
                newParams[i] = switch (role) {
                    case X -> relative ? v * scale : (v + tx) * scale + offsetX;
                    case Y -> relative ? v * scale : (v + ty) * scale + offsetY;
                    case SCALE_ONLY -> v * scale;
                    case KEEP -> v;
                };
            }
            result.add(new PathCommand(cmd.type, newParams));
        }
        return result;
    }

    // ==================== Serialization ====================

    /**
     * Serializes structured commands back to an SVG path data string.
     */
    @NotNull
    private static String serialize(@NotNull List<PathCommand> commands, int precision) {
        StringBuilder sb = new StringBuilder();
        for (PathCommand cmd : commands) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(cmd.type);
            char upper = Character.toUpperCase(cmd.type);
            ParamRole[] roles = getParamRoles(upper);

            for (int i = 0; i < cmd.params.length; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                ParamRole role = i < roles.length ? roles[i] : ParamRole.KEEP;
                if (role == ParamRole.KEEP) {
                    // Flags and rotation: output as integers if they are whole numbers
                    double v = cmd.params[i];
                    if (v == (int) v) {
                        sb.append((int) v);
                    } else {
                        sb.append(formatNumber(v, precision));
                    }
                } else {
                    sb.append(formatNumber(cmd.params[i], precision));
                }
            }
        }
        return sb.toString();
    }

    // ==================== Number Formatting ====================

    private static final String[] FMT_CACHE = new String[10];
    static {
        for (int i = 0; i < FMT_CACHE.length; i++) {
            FMT_CACHE[i] = "%." + i + "f";
        }
    }

    @NotNull
    private static String formatNumber(double value, int precision) {
        if (precision == 0) {
            return Long.toString(Math.round(value));
        }
        // Format with specified precision, strip trailing zeros
        String fmt = String.format(FMT_CACHE[Math.min(precision, 9)], value);
        if (fmt.contains(".")) {
            fmt = fmt.replaceAll("0+$", "");
            fmt = fmt.replaceAll("\\.$", "");
        }
        // Avoid "-0"
        if ("-0".equals(fmt)) {
            return "0";
        }
        return fmt;
    }

    @NotNull
    private static List<Double> parseNumbers(@NotNull String text) {
        List<Double> numbers = new ArrayList<>();
        Matcher m = NUM_PATTERN.matcher(text);
        while (m.find()) {
            try {
                numbers.add(Double.parseDouble(m.group()));
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return numbers;
    }
}
