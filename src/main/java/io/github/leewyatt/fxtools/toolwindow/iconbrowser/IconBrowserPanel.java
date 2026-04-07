package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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

    private static final String SEARCH_HISTORY_KEY = "fxtools.iconbrowser.search.history";
    private static final int SEARCH_DEBOUNCE_MS = 300;

    // ==================== Toolbar Components ====================
    private final JButton packButton;
    private final SearchTextField searchField;

    private static final String CARD_GRID = "grid";
    private static final String CARD_EMPTY = "empty";

    // ==================== Results Components ====================
    private final JBLabel statusLabel;
    private final IconGridPanel gridPanel;
    private final PaginationBar paginationBar;
    private final JBScrollPane gridScrollPane;
    private final JPanel cardPanel;
    private final java.awt.CardLayout cardLayout;
    private final JBLabel emptyIcon;
    private final JBLabel emptyText;
    private final HyperlinkLabel emptyAction;

    // ==================== Detail ====================
    private final IconDetailPanel detailPanel;

    // ==================== State ====================
    private final Project project;
    private final Set<String> enabledPackIds = new LinkedHashSet<>();
    private List<IconDataService.IconEntry> currentResults = Collections.emptyList();
    private boolean dataReady;

    public IconBrowserPanel(@NotNull Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        // ==================== Toolbar: Integrated Search Bar ====================
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(JBUI.Borders.empty(4, 8, 4, 8));

        // Integrated bar: [Filter icon + pack label | separator | search field]
        JPanel searchBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(JBColor.border());
                int arc = JBUI.scale(6);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.dispose();
            }
        };
        searchBar.setOpaque(false);

        // Pack button (borderless, acts as prefix): [Filter icon] text [▼]
        packButton = new JButton(FxToolsBundle.message("icon.browser.loading"));
        packButton.setIcon(AllIcons.General.Filter);
        packButton.setBorderPainted(false);
        packButton.setContentAreaFilled(false);
        packButton.setFocusable(false);
        packButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        packButton.addActionListener(e -> showPackFilter());

        // Arrow-down indicator to the right of pack button
        JBLabel arrowLabel = new JBLabel(AllIcons.General.ArrowDown);
        arrowLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        arrowLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showPackFilter();
            }
        });

        // Separator line between pack button and search field
        JPanel separatorPanel = new JPanel(new BorderLayout());
        separatorPanel.setOpaque(false);
        separatorPanel.setBorder(JBUI.Borders.empty(4, 0));
        javax.swing.JSeparator sep = new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL);
        separatorPanel.add(sep);

        JPanel packArea = new JPanel(new BorderLayout());
        packArea.setOpaque(false);
        packArea.add(packButton, BorderLayout.CENTER);
        packArea.add(arrowLabel, BorderLayout.EAST);

        JPanel packWithSep = new JPanel(new BorderLayout());
        packWithSep.setOpaque(false);
        packWithSep.add(packArea, BorderLayout.CENTER);
        packWithSep.add(separatorPanel, BorderLayout.EAST);
        searchBar.add(packWithSep, BorderLayout.WEST);

        // Search field (borderless, integrated into the bar)
        searchField = new SearchTextField(SEARCH_HISTORY_KEY);
        searchField.getTextEditor().getEmptyText().setText(
                FxToolsBundle.message("icon.browser.search.placeholder"));
        searchField.getTextEditor().setBorder(JBUI.Borders.empty(0, 4));
        searchField.getTextEditor().setOpaque(false);
        searchField.setBorder(JBUI.Borders.empty());
        searchField.setOpaque(false);

        Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
        Disposer.register(ApplicationManager.getApplication(), searchAlarm);

        searchField.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(@org.jetbrains.annotations.NotNull javax.swing.event.DocumentEvent e) {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(() -> executeSearch(), SEARCH_DEBOUNCE_MS);
            }
        });
        searchField.getTextEditor().addActionListener(e -> {
            searchAlarm.cancelAllRequests();
            searchField.addCurrentTextToHistory();
            executeSearch();
        });
        searchBar.add(searchField, BorderLayout.CENTER);

        toolbar.add(searchBar, BorderLayout.CENTER);
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

        // Grid + Empty state via CardLayout
        gridPanel = new IconGridPanel();
        gridScrollPane = new JBScrollPane(gridPanel);
        gridScrollPane.getVerticalScrollBar().setUnitIncrement(JBUI.scale(40));

        // Empty state panel (centered icon + text + action link)
        JPanel emptyPanel = new JPanel(new java.awt.GridBagLayout());
        JPanel emptyContent = new JPanel();
        emptyContent.setLayout(new BoxLayout(emptyContent, BoxLayout.Y_AXIS));
        emptyContent.setOpaque(false);

        emptyIcon = new JBLabel();
        emptyIcon.setIcon(AllIcons.Actions.Search);
        emptyIcon.setAlignmentX(CENTER_ALIGNMENT);
        emptyContent.add(emptyIcon);
        emptyContent.add(Box.createVerticalStrut(JBUI.scale(8)));

        emptyText = new JBLabel();
        emptyText.setForeground(UIUtil.getContextHelpForeground());
        emptyText.setAlignmentX(CENTER_ALIGNMENT);
        emptyContent.add(emptyText);
        emptyContent.add(Box.createVerticalStrut(JBUI.scale(4)));

        emptyAction = new HyperlinkLabel();
        emptyAction.setAlignmentX(CENTER_ALIGNMENT);
        emptyContent.add(emptyAction);

        emptyPanel.add(emptyContent);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(gridScrollPane, CARD_GRID);
        cardPanel.add(emptyPanel, CARD_EMPTY);

        resultsPanel.add(cardPanel, BorderLayout.CENTER);

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

        // Update status + empty state
        boolean isSearch = !query.isEmpty();
        if (currentResults.isEmpty()) {
            if (enabledPackIds.isEmpty()) {
                // No packs selected
                statusLabel.setText(" ");
                showEmptyState(
                        FxToolsBundle.message("icon.browser.empty.no.packs"),
                        FxToolsBundle.message("icon.browser.empty.select.all"),
                        this::selectAllPacks);
            } else if (isSearch) {
                // Search returned no results
                statusLabel.setText(" ");
                showEmptyState(
                        FxToolsBundle.message("icon.browser.empty.no.results", query),
                        null, null);
            } else {
                statusLabel.setText(FxToolsBundle.message("icon.browser.no.results.browse"));
                showEmptyState(
                        FxToolsBundle.message("icon.browser.no.results.browse"),
                        null, null);
            }
        } else {
            cardLayout.show(cardPanel, CARD_GRID);
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
                enabledPackIds, project, () -> {
                    updatePackButton();
                    updateSearchPlaceholder();
                    executeSearch();
                });
    }

    private void showEmptyState(@NotNull String message,
                                @org.jetbrains.annotations.Nullable String actionText,
                                @org.jetbrains.annotations.Nullable Runnable action) {
        emptyText.setText(message);
        if (actionText != null && action != null) {
            emptyAction.setHyperlinkText(actionText);
            emptyAction.setVisible(true);
            // Remove old listeners, add new one
            for (javax.swing.event.HyperlinkListener l : emptyAction.getListeners(javax.swing.event.HyperlinkListener.class)) {
                emptyAction.removeHyperlinkListener(l);
            }
            emptyAction.addHyperlinkListener(e -> {
                if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                    action.run();
                }
            });
        } else {
            emptyAction.setVisible(false);
        }
        cardLayout.show(cardPanel, CARD_EMPTY);
    }

    private void selectAllPacks() {
        IconDataService service = IconDataService.getInstance();
        enabledPackIds.clear();
        for (IconDataService.PackInfo pack : service.getAllPacks()) {
            enabledPackIds.add(pack.getId());
        }
        updatePackButton();
        updateSearchPlaceholder();
        executeSearch();
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
        searchField.getTextEditor().getEmptyText().setText(
                FxToolsBundle.message("icon.browser.search.placeholder.count", count));
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
