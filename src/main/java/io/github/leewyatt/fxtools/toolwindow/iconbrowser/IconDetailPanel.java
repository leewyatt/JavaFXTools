package io.github.leewyatt.fxtools.toolwindow.iconbrowser;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.leewyatt.fxtools.FxToolsBundle;
import io.github.leewyatt.fxtools.util.FxSvgRenderer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Bottom panel showing details of the selected icon: square preview on the left,
 * single header row on the right containing name + pack + link icon + Copy button.
 *
 * <p>The Copy button adapts to the pack type via {@link IconCopyUtil}:</p>
 * <ul>
 *   <li><b>Non-Ikonli</b> (1 action): labelled "Copy path", click copies directly.</li>
 *   <li><b>Ikonli</b> (multiple actions): labelled "Copy" with a dropdown arrow, opens
 *       an IntelliJ native action popup (same list style as Goto Action).</li>
 * </ul>
 *
 * <p>A copied-to-clipboard notification is shown via the "JavaFX Tools" notification
 * group, replacing the earlier in-button text flip.</p>
 */
public class IconDetailPanel extends JPanel {

    // ==================== Sizing ====================
    private static final int PREVIEW_SIZE = 64;
    private static final int PREVIEW_BOX_PADDING = 8;
    private static final int CARD_ARC = 12;
    private static final int PREVIEW_ARC = 10;

    // ==================== Colors ====================
    private static final JBColor ICON_COLOR =
            new JBColor(new Color(0x3C3C3C), new Color(0xCCCCCC));
    private static final JBColor PREVIEW_BG =
            new JBColor(new Color(0xFFFFFF), new Color(0x3B3D3F));

    // ==================== Components ====================
    private final JPanel previewPanel;
    /** Left-aligned flow holding name + pack name + optional link icon. */
    private final JPanel infoFlow;
    private final JBLabel nameLabel;
    private final JBLabel packNameLabel;
    private final JButton copyButton;
    private final JBLabel noPathHint;
    /** Italic hint shown below the name row when the icon is marked np:true. */
    private final JBLabel cannotRenderHint;

    @Nullable
    private IconDataService.IconEntry currentIcon;
    @Nullable
    private IconDataService service;
    @Nullable
    private Project project;

    public IconDetailPanel() {
        setLayout(new BorderLayout(JBUI.scale(14), 0));
        setBorder(JBUI.Borders.empty(10, 14));
        setOpaque(true);
        setVisible(false);

        // ==================== Preview (rounded card on the left) ====================
        previewPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int arc = JBUI.scale(PREVIEW_ARC);
                g2.setColor(PREVIEW_BG);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(JBColor.border());
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                if (currentIcon != null && service != null) {
                    if (!currentIcon.isRenderable()) {
                        // Placeholder centered in the preview box
                        int ph = JBUI.scale(PREVIEW_SIZE);
                        int px = (w - ph) / 2;
                        int py = (h - ph) / 2;
                        IconPlaceholder.paint(g2, px, py, ph);
                    } else {
                        String pathData = service.getPath(currentIcon);
                        if (pathData != null) {
                            paintSvgPreview(g2, pathData, w, h);
                        }
                    }
                }
                g2.dispose();
            }
        };
        previewPanel.setOpaque(false);
        int boxSize = JBUI.scale(PREVIEW_SIZE + PREVIEW_BOX_PADDING);
        previewPanel.setPreferredSize(new Dimension(boxSize, boxSize));
        previewPanel.setMinimumSize(new Dimension(boxSize, boxSize));
        previewPanel.setMaximumSize(new Dimension(boxSize, boxSize));

        JPanel previewWrapper = new JPanel(new GridBagLayout());
        previewWrapper.setOpaque(false);
        previewWrapper.add(previewPanel);
        add(previewWrapper, BorderLayout.WEST);

        // ==================== Info flow: name + pack + link ====================
        infoFlow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        infoFlow.setOpaque(false);
        infoFlow.setAlignmentX(Component.LEFT_ALIGNMENT);

        nameLabel = new JBLabel();
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD,
                nameLabel.getFont().getSize2D() + 2f));
        infoFlow.add(nameLabel);

        packNameLabel = new JBLabel();
        packNameLabel.setForeground(UIUtil.getContextHelpForeground());
        infoFlow.add(packNameLabel);

        // Second line: italic hint shown only for np:true icons
        cannotRenderHint = new JBLabel(FxToolsBundle.message("icon.browser.cannot.render"));
        cannotRenderHint.setForeground(UIUtil.getContextHelpForeground());
        cannotRenderHint.setFont(cannotRenderHint.getFont().deriveFont(Font.ITALIC));
        cannotRenderHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        // FlowLayout defaults to 5px horizontal insets — align the hint's visible text
        // with the first component of infoFlow (nameLabel) by matching the left pad.
        cannotRenderHint.setBorder(JBUI.Borders.emptyLeft(5));
        cannotRenderHint.setVisible(false);

        // Vertical stack: row 1 = infoFlow, row 2 = cannotRenderHint (optional)
        JPanel infoVertical = new JPanel();
        infoVertical.setLayout(new BoxLayout(infoVertical, BoxLayout.Y_AXIS));
        infoVertical.setOpaque(false);
        infoVertical.add(infoFlow);
        infoVertical.add(cannotRenderHint);

        // ==================== Action area: Copy button (or no-path hint) ====================
        copyButton = new JButton();
        copyButton.setHorizontalTextPosition(SwingConstants.LEADING);
        copyButton.addActionListener(e -> handleCopyButtonClick());

        noPathHint = new JBLabel(FxToolsBundle.message("icon.browser.detail.no.path"));
        noPathHint.setForeground(UIUtil.getContextHelpForeground());
        noPathHint.setFont(noPathHint.getFont().deriveFont(Font.ITALIC));
        noPathHint.setVisible(false);

        JPanel actionArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionArea.setOpaque(false);
        actionArea.add(copyButton);
        actionArea.add(noPathHint);

        // ==================== Header row (info column + action area) ====================
        JPanel headerRow = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        headerRow.setOpaque(false);
        headerRow.add(infoVertical, BorderLayout.CENTER);
        headerRow.add(actionArea, BorderLayout.EAST);

        // Wrap so the single row takes only its preferred height and is vertically
        // centered within the CENTER area (so the button doesn't stretch to 72px tall).
        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        rightWrapper.add(headerRow, gbc);
        add(rightWrapper, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = JBUI.scale(CARD_ARC);
        // g2.setColor(CARD_BG);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        g2.setColor(JBColor.border());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }

    // ==================== Public API ====================

    public void showIcon(@Nullable IconDataService.IconEntry icon,
                         @Nullable IconDataService service,
                         @Nullable Project project) {
        this.currentIcon = icon;
        this.project = project;
        this.service = service;

        if (icon == null) {
            setVisible(false);
            return;
        }
        setVisible(true);

        IconDataService.PackInfo pack = icon.getPack();

        // ---- Info flow: name + pack + link icon ----
        nameLabel.setText(icon.getName());
        packNameLabel.setText(pack.getName());
        // Remove any previously-added link icon (index 0 and 1 are the fixed labels)
        while (infoFlow.getComponentCount() > 2) {
            infoFlow.remove(infoFlow.getComponentCount() - 1);
        }
        String url = pack.getSourceUrl();
        if (url != null && !url.isEmpty()) {
            Icon linkIcon = url.contains("github.com")
                    ? AllIcons.Vcs.Vendors.Github
                    : AllIcons.General.Web;
            JBLabel linkLabel = new JBLabel(linkIcon);
            linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            linkLabel.setToolTipText(url);
            linkLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    BrowserUtil.browse(url);
                }
            });
            infoFlow.add(linkLabel);
        }

        // ---- "Cannot render" hint (second info line) ----
        cannotRenderHint.setVisible(!icon.isRenderable());

        // ---- Copy button / no-path hint state ----
        List<IconCopyUtil.CopyItem> items = IconCopyUtil.buildItems(icon, service, project);
        if (items.isEmpty()) {
            copyButton.setVisible(false);
            noPathHint.setVisible(true);
        } else if (items.size() == 1) {
            noPathHint.setVisible(false);
            copyButton.setVisible(true);
            copyButton.setIcon(null);
            copyButton.setText(items.get(0).label());
        } else {
            noPathHint.setVisible(false);
            copyButton.setVisible(true);
            copyButton.setIcon(AllIcons.General.ArrowDown);
            copyButton.setText(FxToolsBundle.message("icon.browser.detail.copy.button"));
        }

        infoFlow.revalidate();
        revalidate();
        repaint();
    }

    // ==================== Copy button click ====================

    private void handleCopyButtonClick() {
        if (currentIcon == null) {
            return;
        }
        IconCopyUtil.showPopupUnderneath(copyButton, currentIcon, service, project);
    }

    // ==================== SVG preview rendering ====================

    private void paintSvgPreview(Graphics2D g, String pathData, int w, int h) {
        GeneralPath path = FxSvgRenderer.parseSvgPath(pathData);
        if (path == null) {
            return;
        }
        Rectangle2D bounds = path.getBounds2D();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return;
        }

        int size = JBUI.scale(PREVIEW_SIZE);
        double scale = Math.min((size - 4) / bounds.getWidth(),
                (size - 4) / bounds.getHeight());
        double tx = (w - bounds.getWidth() * scale) / 2 - bounds.getX() * scale;
        double ty = (h - bounds.getHeight() * scale) / 2 - bounds.getY() * scale;
        AffineTransform transform = new AffineTransform();
        transform.translate(tx, ty);
        transform.scale(scale, scale);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(ICON_COLOR);
        g2.fill(transform.createTransformedShape(path));
        g2.dispose();
    }
}
