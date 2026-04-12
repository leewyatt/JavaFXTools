package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.openapi.project.Project;
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
import javax.swing.JSeparator;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Popup for selecting which icon pack <em>groups</em> to include in search/browse.
 *
 * <p>Shows one row per {@link IconDataService.PackGroup}. Toggling a group's checkbox
 * adds or removes all of its member packs' raw packIds from the {@code selected} set
 * atomically — the rest of the app ({@link IconBrowserPanel}, {@link IconSearchEngine})
 * continues to work with raw packIds, so no downstream changes are needed.</p>
 *
 * <p>A group's checkbox is considered "checked" only when <em>all</em> of its member
 * packIds are present in {@code selected}. Partial selection (half-state) is not
 * exposed in the UI — the Swing {@code CheckBoxList} doesn't support it natively, and
 * partial state can only arise transiently if another code path injects raw packIds.</p>
 */
public class PackFilterPopup {

    private PackFilterPopup() {
    }

    /**
     * Shows the pack filter popup below the given component.
     *
     * @param owner       component to anchor the popup to
     * @param allGroups   all aggregated pack groups
     * @param selected    currently selected raw pack IDs (modified in place as groups are toggled)
     * @param onChanged   callback when selection changes (fired immediately on each checkbox toggle)
     */
    public static void show(@NotNull JComponent owner,
                            @NotNull List<IconDataService.PackGroup> allGroups,
                            @NotNull Set<String> selected,
                            @Nullable Project project,
                            @Nullable Runnable onChanged) {

        JPanel content = new JPanel(new BorderLayout());
        content.setPreferredSize(new Dimension(JBUI.scale(300), JBUI.scale(400)));

        // Track which groups are currently visible in the filtered list
        List<IconDataService.PackGroup> visibleGroups = new ArrayList<>(allGroups);

        // ==================== Search Filter ====================
        SearchTextField filterField = new SearchTextField(false);
        filterField.getTextEditor().getEmptyText().setText(FxToolsBundle.message("icon.browser.packs.filter"));
        content.add(filterField, BorderLayout.NORTH);

        // ==================== CheckBox List ====================
        CheckBoxList<IconDataService.PackGroup> checkList = new CheckBoxList<>();
        for (IconDataService.PackGroup group : allGroups) {
            checkList.addItem(group, group.getName() + " (" + group.getTotal() + ")",
                    isGroupSelected(group, selected));
        }

        JBScrollPane scrollPane = new JBScrollPane(checkList);
        content.add(scrollPane, BorderLayout.CENTER);

        // Sync only visible groups and fire callback
        Runnable syncAndNotify = () -> {
            for (IconDataService.PackGroup group : visibleGroups) {
                if (checkList.isItemSelected(group)) {
                    selected.addAll(group.getPackIds());
                } else {
                    group.getPackIds().forEach(selected::remove);
                }
            }
            if (onChanged != null) {
                onChanged.run();
            }
        };

        // Listen for checkbox toggles
        checkList.setCheckBoxListListener((index, value) -> syncAndNotify.run());

        // ==================== Bottom Bar ====================
        JPanel bottomArea = new JPanel();
        bottomArea.setLayout(new javax.swing.BoxLayout(bottomArea, javax.swing.BoxLayout.Y_AXIS));
        bottomArea.setBorder(JBUI.Borders.empty(2, 4, 4, 4));

        // Row 1: Select Project Deps (global operation, closes popup)
        // Populated after popup is created so we can call popup.cancel()

        // Row 2: Clear / Select All (local operations on visible items)
        JPanel localRow = new JPanel(new GridLayout(1, 2, JBUI.scale(8), 0));
        localRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton clearAll = new JButton(FxToolsBundle.message("icon.browser.packs.clear"));
        clearAll.addActionListener(e -> {
            for (IconDataService.PackGroup group : visibleGroups) {
                checkList.setItemSelected(group, false);
            }
            checkList.repaint();
            syncAndNotify.run();
        });

        JButton selectAll = new JButton(FxToolsBundle.message("icon.browser.packs.select.all"));
        selectAll.addActionListener(e -> {
            for (IconDataService.PackGroup group : visibleGroups) {
                checkList.setItemSelected(group, true);
            }
            checkList.repaint();
            syncAndNotify.run();
        });

        localRow.add(clearAll);
        localRow.add(selectAll);
        bottomArea.add(localRow);

        content.add(bottomArea, BorderLayout.SOUTH);

        // ==================== Filter Logic ====================
        filterField.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull javax.swing.event.DocumentEvent e) {
                String text = filterField.getText().toLowerCase().trim();
                visibleGroups.clear();
                checkList.clear();
                for (IconDataService.PackGroup group : allGroups) {
                    if (text.isEmpty() || group.getName().toLowerCase().contains(text)) {
                        visibleGroups.add(group);
                        checkList.addItem(group, group.getName() + " (" + group.getTotal() + ")",
                                isGroupSelected(group, selected));
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

        // Row 1: Select Project Deps (needs popup reference to close)
        if (project != null) {
            JButton depsButton = new JButton(FxToolsBundle.message("icon.browser.packs.deps"));
            depsButton.setToolTipText(FxToolsBundle.message("icon.browser.packs.deps.tooltip"));
            depsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            depsButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, depsButton.getPreferredSize().height));
            depsButton.addActionListener(e -> {
                Set<String> available = IconDataService.getAvailablePacks(project);
                selected.clear();
                selected.addAll(available);
                if (onChanged != null) {
                    onChanged.run();
                }
                popup.cancel();
            });
            // Insert deps button + separator before localRow
            bottomArea.add(depsButton, 0);
            JSeparator separator = new JSeparator();
            separator.setAlignmentX(Component.LEFT_ALIGNMENT);
            separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            bottomArea.add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)), 1);
            bottomArea.add(separator, 2);
            bottomArea.add(javax.swing.Box.createVerticalStrut(JBUI.scale(4)), 3);
        }

        popup.showUnderneathOf(owner);
    }

    /**
     * Returns true iff every member pack of the group is present in {@code selected}.
     * Partial selection (some members in, some out) is treated as "not selected".
     */
    private static boolean isGroupSelected(@NotNull IconDataService.PackGroup group,
                                           @NotNull Set<String> selected) {
        for (String packId : group.getPackIds()) {
            if (!selected.contains(packId)) {
                return false;
            }
        }
        return true;
    }
}
