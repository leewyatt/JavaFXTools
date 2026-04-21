package io.github.leewyatt.fxtools.fxmlkit.devmode;

/**
 * Shared constants for the FxmlKit Dev Mode Executor feature.
 */
public final class FxmlKitDevModeConstants {

    public static final String EXECUTOR_ID_RUN = "FxmlKit.DevMode";
    public static final String EXECUTOR_ID_DEBUG = "FxmlKit.DevModeDebug";

    public static final String RUNNER_ID_RUN = "FxmlKit.DevModeRunner";
    public static final String RUNNER_ID_DEBUG = "FxmlKit.DevModeDebugRunner";

    public static final String SYS_PROP_KEY = "fxmlkit.devmode";
    public static final String SYS_PROP_VALUE = "true";

    /** FxmlKit versions &lt; this value ignore {@code -Dfxmlkit.devmode=true}. */
    public static final String MIN_SUPPORTED_VERSION = "1.5.1";

    private FxmlKitDevModeConstants() {
    }
}
