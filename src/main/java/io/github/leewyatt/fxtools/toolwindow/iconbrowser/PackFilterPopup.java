package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Popup for selecting which icon packs to include in search/browse.
 */
public class PackFilterPopup {

    private PackFilterPopup() {
    }

    /**
     * Shows the pack filter popup below the given component.
     *
     * @param owner       component to anchor the popup to
     * @param allPacks    all available packs
     * @param selected    currently selected pack IDs (modified in place)
     * @param onChanged   callback when selection changes (fired immediately on each checkbox toggle)
     */
    public static void show(@NotNull JComponent owner,
                            @NotNull List<IconDataService.PackInfo> allPacks,
                            @NotNull Set<String> selected,
                            @Nullable Runnable onChanged) {

        JPanel content = new JPanel(new BorderLayout());
        content.setPreferredSize(new Dimension(JBUI.scale(300), JBUI.scale(400)));

        // Track which packs are currently visible in the filtered list
        List<IconDataService.PackInfo> visiblePacks = new ArrayList<>(allPacks);

        // ==================== Search Filter ====================
        SearchTextField filterField = new SearchTextField(false);
        filterField.getTextEditor().getEmptyText().setText(FxToolsBundle.message("icon.browser.packs.filter"));
        content.add(filterField, BorderLayout.NORTH);

        // ==================== CheckBox List ====================
        CheckBoxList<IconDataService.PackInfo> checkList = new CheckBoxList<>();
        for (IconDataService.PackInfo pack : allPacks) {
            checkList.addItem(pack, pack.getName() + " (" + pack.getTotal() + ")",
                    selected.contains(pack.getId()));
        }

        JBScrollPane scrollPane = new JBScrollPane(checkList);
        content.add(scrollPane, BorderLayout.CENTER);

        // Sync only visible packs and fire callback
        Runnable syncAndNotify = () -> {
            for (IconDataService.PackInfo pack : visiblePacks) {
                if (checkList.isItemSelected(pack)) {
                    selected.add(pack.getId());
                } else {
                    selected.remove(pack.getId());
                }
            }
            if (onChanged != null) {
                onChanged.run();
            }
        };

        // Listen for checkbox toggles
        checkList.setCheckBoxListListener((index, value) -> syncAndNotify.run());

        // ==================== Bottom Bar ====================
        JPanel bottomBar = new JPanel(new GridLayout(1, 2, JBUI.scale(8), 0));
        bottomBar.setBorder(JBUI.Borders.empty(2, 4, 4, 4));

        JButton selectAll = new JButton(FxToolsBundle.message("icon.browser.packs.select.all"));
        selectAll.addActionListener(e -> {
            for (int i = 0; i < visiblePacks.size(); i++) {
                checkList.setItemSelected(visiblePacks.get(i), true);
            }
            checkList.repaint();
            syncAndNotify.run();
        });

        JButton clearAll = new JButton(FxToolsBundle.message("icon.browser.packs.clear"));
        clearAll.addActionListener(e -> {
            for (int i = 0; i < visiblePacks.size(); i++) {
                checkList.setItemSelected(visiblePacks.get(i), false);
            }
            checkList.repaint();
            syncAndNotify.run();
        });

        bottomBar.add(clearAll);
        bottomBar.add(selectAll);

        content.add(bottomBar, BorderLayout.SOUTH);

        // ==================== Filter Logic ====================
        filterField.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull javax.swing.event.DocumentEvent e) {
                String text = filterField.getText().toLowerCase().trim();
                visiblePacks.clear();
                checkList.clear();
                for (IconDataService.PackInfo pack : allPacks) {
                    if (text.isEmpty() || pack.getName().toLowerCase().contains(text)) {
                        visiblePacks.add(pack);
                        checkList.addItem(pack, pack.getName() + " (" + pack.getTotal() + ")",
                                selected.contains(pack.getId()));
                    }
                }
            }
        });

        // ==================== Popup ====================
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(content, filterField.getTextEditor())
                .setTitle(FxToolsBundle.message("icon.browser.packs.title"))
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .setCancelCallback(() -> true)
                .setCancelButton(new com.intellij.openapi.ui.popup.IconButton(
                        FxToolsBundle.message("icon.browser.packs.close"),
                        com.intellij.icons.AllIcons.Actions.Close,
                        com.intellij.icons.AllIcons.Actions.CloseHovered))
                .createPopup();

        popup.showUnderneathOf(owner);
    }
}
