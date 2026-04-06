package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Compact pagination bar with narrow prev/next buttons and page indicator.
 */
public class PaginationBar extends JPanel {

    public static final int PAGE_SIZE = 100;

    private final JButton prevButton;
    private final JButton nextButton;
    private final JBLabel pageLabel;

    private int totalItems;
    private int currentPage;

    @Nullable
    private Runnable onPageChanged;

    public PaginationBar() {
        setLayout(new FlowLayout(FlowLayout.CENTER, JBUI.scale(2), 0));
        setOpaque(false);

        prevButton = createArrowButton("<");
        prevButton.addActionListener(e -> {
            if (currentPage > 0) {
                currentPage--;
                updateState();
                firePageChanged();
            }
        });

        nextButton = createArrowButton(">");
        nextButton.addActionListener(e -> {
            if (currentPage < getTotalPages() - 1) {
                currentPage++;
                updateState();
                firePageChanged();
            }
        });

        pageLabel = new JBLabel();

        add(prevButton);
        add(pageLabel);
        add(nextButton);

        updateState();
    }

    private static JButton createArrowButton(String text) {
        JButton btn = new JButton(text);
        btn.putClientProperty("JButton.buttonType", "toolBarButton");
        btn.setMargin(JBUI.insets(1, 3));
        btn.setFont(btn.getFont().deriveFont((float) JBUI.scale(11)));
        Dimension size = new Dimension(JBUI.scale(35), JBUI.scale(30));
        btn.setPreferredSize(size);
        btn.setMinimumSize(size);
        btn.setMaximumSize(size);
        return btn;
    }

    // ==================== Public API ====================

    public void setOnPageChanged(@Nullable Runnable listener) {
        this.onPageChanged = listener;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
        this.currentPage = 0;
        updateState();
    }

    public int getCurrentPage() { return currentPage; }

    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
    }

    /**
     * Returns the start index (inclusive) for the current page.
     */
    public int getPageStart() {
        return currentPage * PAGE_SIZE;
    }

    /**
     * Returns the end index (exclusive) for the current page.
     */
    public int getPageEnd() {
        return Math.min(getPageStart() + PAGE_SIZE, totalItems);
    }

    // ==================== Internal ====================

    private void updateState() {
        int total = getTotalPages();
        pageLabel.setText(" " + (currentPage + 1) + " / " + total + " ");
        prevButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < total - 1);
        setVisible(total > 1);
    }

    private void firePageChanged() {
        if (onPageChanged != null) {
            onPageChanged.run();
        }
    }
}
