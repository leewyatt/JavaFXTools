package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Main container panel for the Icon Browser tool window.
 * Manages toolbar, grid, pagination, and detail panel.
 */
public class IconBrowserPanel extends JPanel {

    // ==================== Toolbar Components ====================
    private final JButton packButton;
    private final JBTextField searchField;
    private final JButton actionButton;

    // ==================== Results Components ====================
    private final JBLabel statusLabel;
    private final IconGridPanel gridPanel;
    private final PaginationBar paginationBar;
    private final JBScrollPane gridScrollPane;

    // ==================== Detail ====================
    private final IconDetailPanel detailPanel;

    // ==================== State ====================
    private final Set<String> enabledPackIds = new LinkedHashSet<>();
    private List<IconDataService.IconEntry> currentResults = Collections.emptyList();
    private boolean dataReady;

    public IconBrowserPanel() {
        setLayout(new BorderLayout());

        // ==================== Toolbar ====================
        JPanel toolbar = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        toolbar.setBorder(JBUI.Borders.empty(4, 8, 4, 8));

        packButton = new JButton(FxToolsBundle.message("icon.browser.loading"));
        packButton.setPreferredSize(new Dimension(JBUI.scale(150), packButton.getPreferredSize().height));
        packButton.addActionListener(e -> showPackFilter());
        toolbar.add(packButton, BorderLayout.WEST);

        searchField = new JBTextField();
        searchField.getEmptyText().setText(FxToolsBundle.message("icon.browser.search.placeholder"));
        searchField.addActionListener(e -> executeSearch());
        toolbar.add(searchField, BorderLayout.CENTER);

        actionButton = new JButton(FxToolsBundle.message("icon.browser.search.button"));
        actionButton.addActionListener(e -> executeSearch());
        toolbar.add(actionButton, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);

        // ==================== Center: Results Panel ====================
        JPanel resultsPanel = new JPanel(new BorderLayout());

        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(JBUI.Borders.empty(5));
        statusLabel = new JBLabel(" ");
        statusLabel.setForeground(UIUtil.getContextHelpForeground());
        paginationBar = new PaginationBar();
        paginationBar.setOnPageChanged(this::onPageChanged);
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(paginationBar, BorderLayout.EAST);
        resultsPanel.add(statusBar, BorderLayout.NORTH);

        // Grid
        gridPanel = new IconGridPanel();
        gridScrollPane = new JBScrollPane(gridPanel);
        gridScrollPane.getVerticalScrollBar().setUnitIncrement(JBUI.scale(40));
        resultsPanel.add(gridScrollPane, BorderLayout.CENTER);

        add(resultsPanel, BorderLayout.CENTER);

        // ==================== Detail Panel ====================
        detailPanel = new IconDetailPanel();
        add(detailPanel, BorderLayout.SOUTH);

        // Wire grid selection to detail panel (after both are initialized)
        gridPanel.setSelectionListener(icon -> detailPanel.showIcon(icon, IconDataService.getInstance()));

        // ==================== Initial Data Load ====================
        loadDataAsync();
    }

    // ==================== Data Loading ====================

    private void loadDataAsync() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            IconDataService service = IconDataService.getInstance();
            service.ensureLoaded();

            SwingUtilities.invokeLater(() -> {
                dataReady = true;
                // Default: all packs selected
                for (IconDataService.PackInfo pack : service.getAllPacks()) {
                    enabledPackIds.add(pack.getId());
                }
                updatePackButton();
                updateSearchPlaceholder();
                executeSearch();
            });
        });
    }

    // ==================== Search ====================

    private void executeSearch() {
        if (!dataReady) {
            return;
        }

        IconDataService service = IconDataService.getInstance();
        String query = searchField.getText().trim();

        currentResults = IconSearchEngine.search(
                query.isEmpty() ? null : query,
                enabledPackIds,
                service);

        // Update status
        boolean isSearch = !query.isEmpty();
        if (currentResults.isEmpty()) {
            statusLabel.setText(isSearch
                    ? FxToolsBundle.message("icon.browser.no.results.search", query)
                    : FxToolsBundle.message("icon.browser.no.results.browse"));
        } else {
            int packCount = countDistinctPacks(currentResults);
            if (isSearch) {
                statusLabel.setText(FxToolsBundle.message("icon.browser.results.search", query, currentResults.size()));
            } else if (enabledPackIds.size() == 1) {
                IconDataService.PackInfo pack = currentResults.get(0).getPack();
                String license = pack.getLicense();
                statusLabel.setText(license != null && !license.isEmpty()
                        ? FxToolsBundle.message("icon.browser.results.single.pack.license",
                                pack.getName(), currentResults.size(), license)
                        : FxToolsBundle.message("icon.browser.results.single.pack",
                                pack.getName(), currentResults.size()));
            } else {
                statusLabel.setText(FxToolsBundle.message("icon.browser.results.multi.pack",
                        packCount, currentResults.size()));
            }
        }

        // Pagination
        paginationBar.setTotalItems(currentResults.size());
        detailPanel.showIcon(null, null);
        loadCurrentPage();
    }

    private void onPageChanged() {
        loadCurrentPage();
    }

    private void loadCurrentPage() {
        if (currentResults.isEmpty()) {
            gridPanel.setPageData(Collections.emptyList(), IconDataService.getInstance(), false);
            return;
        }

        int start = paginationBar.getPageStart();
        int end = paginationBar.getPageEnd();
        List<IconDataService.IconEntry> pageItems = currentResults.subList(
                Math.min(start, currentResults.size()),
                Math.min(end, currentResults.size()));

        boolean showTags = countDistinctPacks(pageItems) > 1;

        // Load needed packs in background
        IconDataService service = IconDataService.getInstance();
        Set<String> neededPacks = new HashSet<>();
        for (IconDataService.IconEntry item : pageItems) {
            neededPacks.add(item.getPackId());
        }

        // Check if all needed packs are loaded
        boolean allLoaded = neededPacks.stream().allMatch(service::isPackLoaded);

        // Show grid immediately (with or without icons)
        gridPanel.setPageData(pageItems, service, showTags);
        gridScrollPane.getVerticalScrollBar().setValue(0);

        if (!allLoaded) {
            // Load missing packs in background, then refresh grid
            List<IconDataService.IconEntry> finalPageItems = pageItems;
            boolean finalShowTags = showTags;
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                for (String packId : neededPacks) {
                    IconDataService.PackInfo pack = findPack(packId);
                    if (pack != null) {
                        service.ensurePackLoaded(pack);
                    }
                }
                SwingUtilities.invokeLater(() ->
                        gridPanel.setPageData(finalPageItems, service, finalShowTags));
            });
        }
    }

    // ==================== Pack Filter ====================

    private void showPackFilter() {
        if (!dataReady) {
            return;
        }
        PackFilterPopup.show(packButton, IconDataService.getInstance().getAllPacks(),
                enabledPackIds, () -> {
                    updatePackButton();
                    updateSearchPlaceholder();
                    executeSearch();
                });
    }

    private void updatePackButton() {
        IconDataService service = IconDataService.getInstance();
        int total = service.getAllPacks().size();
        int selected = enabledPackIds.size();
        if (selected == total) {
            packButton.setText(FxToolsBundle.message("icon.browser.packs.all", total));
        } else if (selected == 0) {
            packButton.setText(FxToolsBundle.message("icon.browser.packs.none"));
        } else {
            packButton.setText(FxToolsBundle.message("icon.browser.packs.count", selected));
        }
    }

    private void updateSearchPlaceholder() {
        if (!dataReady) {
            return;
        }
        int count = IconDataService.getInstance().countIcons(enabledPackIds);
        searchField.getEmptyText().setText(FxToolsBundle.message("icon.browser.search.placeholder.count", count));
    }

    // ==================== Utilities ====================

    private IconDataService.PackInfo findPack(String packId) {
        for (IconDataService.PackInfo p : IconDataService.getInstance().getAllPacks()) {
            if (p.getId().equals(packId)) {
                return p;
            }
        }
        return null;
    }

    private static int countDistinctPacks(@NotNull List<IconDataService.IconEntry> icons) {
        Set<String> packs = new HashSet<>();
        for (IconDataService.IconEntry icon : icons) {
            packs.add(icon.getPackId());
        }
        return packs.size();
    }
}
