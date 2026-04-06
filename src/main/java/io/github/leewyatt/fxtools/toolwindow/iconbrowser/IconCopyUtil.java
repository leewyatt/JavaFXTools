package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.awt.RelativePoint;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Point;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared builder/runner for icon copy actions. Used by both {@link IconDetailPanel}
 * (single Copy button) and {@link IconGridPanel} (right-click context menu) to guarantee
 * identical behaviour and styling.
 *
 * <p>Uses {@link JBPopupFactory#createActionGroupPopup} so the popup is the same
 * list-based component IntelliJ uses for Goto Action / Recent Files — this gets the
 * correct light-blue hover colour and keyboard navigation automatically. Raw
 * {@code JBPopupMenu} + {@code JMenuItem} would have given us the dark-blue
 * {@code DarculaMenuItemUI} hover instead.</p>
 */
public final class IconCopyUtil {

    private IconCopyUtil() {
    }

    /** A single copyable snippet derived from an icon. */
    public record CopyItem(@NotNull String label, @NotNull String value, boolean separatorBefore) {
    }

    // ==================== Building ====================

    /**
     * Builds the copy items for an icon. Non-Ikonli packs contribute only "Copy path"
     * (if path data is loaded); Ikonli packs additionally contribute Java/CSS/Maven/Gradle
     * snippets, with a separator before Maven to visually group code vs dependency entries.
     */
    @NotNull
    public static List<CopyItem> buildItems(@NotNull IconDataService.IconEntry icon,
                                             @Nullable IconDataService service) {
        List<CopyItem> items = new ArrayList<>();
        String pathData = service != null ? service.getPath(icon) : null;
        if (pathData != null) {
            items.add(new CopyItem(
                    FxToolsBundle.message("icon.browser.detail.copy.path"),
                    pathData, false));
        }

        IconDataService.PackInfo pack = icon.getPack();
        if (pack.isIkonli()) {
            String enumClass = icon.getEffectiveEnumClass();
            String constantName = icon.getEnumConstantName();
            if (enumClass != null && constantName != null) {
                int lastDot = enumClass.lastIndexOf('.');
                String enumSimple = lastDot >= 0 ? enumClass.substring(lastDot + 1) : enumClass;
                items.add(new CopyItem(
                        FxToolsBundle.message("icon.browser.detail.copy.java"),
                        enumSimple + "." + constantName, false));
            }
            items.add(new CopyItem(
                    FxToolsBundle.message("icon.browser.detail.copy.css"),
                    "-fx-icon-code: \"" + icon.getLiteral() + "\";", false));

            if (pack.getMaven() != null && service != null) {
                String[] parts = pack.getMaven().split(":");
                String version = service.getIkonliVersion();
                if (parts.length == 2) {
                    String maven = "<dependency>\n    <groupId>" + parts[0] + "</groupId>\n"
                            + "    <artifactId>" + parts[1] + "</artifactId>\n"
                            + "    <version>" + version + "</version>\n</dependency>";
                    items.add(new CopyItem(
                            FxToolsBundle.message("icon.browser.detail.copy.maven"),
                            maven, true));
                }
                items.add(new CopyItem(
                        FxToolsBundle.message("icon.browser.detail.copy.gradle"),
                        "implementation '" + pack.getMaven() + ":" + version + "'", false));
            }
        }
        return items;
    }

    // ==================== Showing the popup ====================

    /**
     * Shows the copy menu underneath the given anchor component (used by the Copy
     * button in IconDetailPanel). If only one item is available, copies directly
     * without showing a menu.
     */
    public static void showPopupUnderneath(@NotNull Component anchor,
                                            @NotNull IconDataService.IconEntry icon,
                                            @Nullable IconDataService service) {
        List<CopyItem> items = buildItems(icon, service);
        if (items.isEmpty()) {
            return;
        }
        if (items.size() == 1) {
            performCopy(items.get(0));
            return;
        }
        createPopup(anchor, items).showUnderneathOf(anchor);
    }

    /**
     * Shows the copy menu at the given point relative to the anchor (used by
     * IconGridPanel right-click handling).
     */
    public static void showPopupAt(@NotNull Component anchor,
                                    @NotNull IconDataService.IconEntry icon,
                                    @Nullable IconDataService service,
                                    int x, int y) {
        List<CopyItem> items = buildItems(icon, service);
        if (items.isEmpty()) {
            return;
        }
        if (items.size() == 1) {
            performCopy(items.get(0));
            return;
        }
        createPopup(anchor, items).show(new RelativePoint(anchor, new Point(x, y)));
    }

    @NotNull
    private static ListPopup createPopup(@NotNull Component anchor,
                                          @NotNull List<CopyItem> items) {
        DefaultActionGroup group = new DefaultActionGroup();
        for (CopyItem item : items) {
            if (item.separatorBefore()) {
                group.addSeparator();
            }
            group.add(new AnAction(item.label()) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    performCopy(item);
                }
            });
        }
        return JBPopupFactory.getInstance().createActionGroupPopup(
                null, group,
                DataManager.getInstance().getDataContext(anchor),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true);
    }

    // ==================== Copy ====================

    private static void performCopy(@NotNull CopyItem item) {
        CopyPasteManager.getInstance().setContents(new StringSelection(item.value()));
    }
}
