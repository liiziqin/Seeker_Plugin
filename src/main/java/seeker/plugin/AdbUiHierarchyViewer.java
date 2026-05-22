package seeker.plugin;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;

public class AdbUiHierarchyViewer extends JFrame {
    private final DefaultListModel<String> deviceListModel = new DefaultListModel<>();
    private final JList<String> deviceList = new JList<>(deviceListModel);
    private final JTextArea xmlTextArea = new JTextArea();
    private final JTextArea textBoundsArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("● 準備就緒");
    private final JButton refreshButton = new JButton("刷新裝置清單");
    private final JButton dumpXmlButton = new JButton("取得目前畫面 XML");
    private final JButton restartSolflareButton = new JButton("重開 Solflare");

    private static final Color BACKGROUND_TOP = new Color(9, 12, 22);
    private static final Color BACKGROUND_BOTTOM = new Color(24, 19, 45);
    private static final Color PANEL_BG = new Color(18, 24, 42, 215);
    private static final Color PANEL_BORDER = new Color(112, 140, 255, 75);
    private static final Color TEXT_MAIN = new Color(235, 242, 255);
    private static final Color TEXT_MUTED = new Color(143, 157, 186);
    private static final Color ACCENT_BLUE = new Color(77, 166, 255);
    public AdbUiHierarchyViewer() {
        initUI();
        refreshDeviceList();
    }

    private void initUI() {
        UIManager.put("Button.arc", 18);
        UIManager.put("Component.arc", 18);
        setTitle("Seeker - UI Hierarchy 測試工具");
        setSize(1120, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new BackgroundPanel();
        mainPanel.setLayout(new BorderLayout(18, 18));
        mainPanel.setBorder(new EmptyBorder(22, 24, 22, 24));
        setContentPane(mainPanel);

        JPanel headerPanel = new JPanel(new BorderLayout(12, 6));
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("Seeker UI Matrix");
        titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 26));
        titleLabel.setForeground(TEXT_MAIN);
        JLabel subtitleLabel = new JLabel("即時讀取 Android UI hierarchy，解析文字 bounds 與中心座標");
        subtitleLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        subtitleLabel.setForeground(TEXT_MUTED);
        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 3));
        titleBlock.setOpaque(false);
        titleBlock.add(titleLabel);
        titleBlock.add(subtitleLabel);
        headerPanel.add(titleBlock, BorderLayout.WEST);
        headerPanel.add(statusPill(), BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        deviceList.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 14));
        deviceList.setBackground(new Color(14, 20, 36));
        deviceList.setForeground(TEXT_MAIN);
        deviceList.setSelectionBackground(new Color(74, 120, 255));
        deviceList.setFixedCellHeight(42);
        deviceList.setBorder(new EmptyBorder(6, 6, 6, 6));
        deviceList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
                label.setOpaque(true);
                label.setForeground(isSelected ? Color.WHITE : new Color(212, 223, 245));
                label.setBackground(isSelected ? new Color(57, 91, 194) : new Color(14, 20, 36));
                label.setBorder(new EmptyBorder(0, 14, 0, 14));
                return label;
            }
        });

        JPanel leftPanel = new GlassPanel(24);
        leftPanel.setLayout(new BorderLayout(12, 14));
        leftPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        leftPanel.add(sectionHeader("ADB 裝置清單", "選擇要解析的手機"), BorderLayout.NORTH);
        leftPanel.add(styleScrollPane(new JScrollPane(deviceList)), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        buttonPanel.setOpaque(false);
        refreshButton.setFocusable(false);
        dumpXmlButton.setFocusable(false);
        restartSolflareButton.setFocusable(false);
        styleSecondaryButton(refreshButton);
        stylePrimaryButton(dumpXmlButton);
        styleSecondaryButton(restartSolflareButton);
        refreshButton.addActionListener(e -> refreshDeviceList());
        dumpXmlButton.addActionListener(e -> dumpSelectedDeviceXml());
        restartSolflareButton.addActionListener(e -> restartSelectedDeviceSolflare());
        buttonPanel.add(refreshButton);
        buttonPanel.add(dumpXmlButton);
        buttonPanel.add(restartSolflareButton);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        xmlTextArea.setEditable(false);
        xmlTextArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        xmlTextArea.setMargin(new Insets(14, 14, 14, 14));
        xmlTextArea.setBackground(new Color(10, 15, 28));
        xmlTextArea.setForeground(new Color(214, 225, 247));
        xmlTextArea.setCaretColor(ACCENT_BLUE);
        xmlTextArea.setText("請選擇裝置後按下「取得目前畫面 XML」。");

        textBoundsArea.setEditable(false);
        textBoundsArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));
        textBoundsArea.setMargin(new Insets(14, 14, 14, 14));
        textBoundsArea.setBackground(new Color(10, 15, 28));
        textBoundsArea.setForeground(new Color(214, 225, 247));
        textBoundsArea.setCaretColor(ACCENT_BLUE);
        textBoundsArea.setText("解析到的文字座標會顯示在這裡。");

        JPanel xmlPanel = titledPanel("原始 XML", "uiautomator dump 回傳的 hierarchy", styleScrollPane(new JScrollPane(xmlTextArea)));
        JPanel boundsPanel = titledPanel("文字與座標", "text / content-desc / bounds / center", styleScrollPane(new JScrollPane(textBoundsArea)));
        JSplitPane rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, xmlPanel, boundsPanel);
        rightSplitPane.setResizeWeight(0.58);
        rightSplitPane.setBorder(BorderFactory.createEmptyBorder());
        rightSplitPane.setDividerSize(10);
        rightSplitPane.setOpaque(false);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplitPane);
        splitPane.setResizeWeight(0.25);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(12);
        splitPane.setOpaque(false);
        mainPanel.add(splitPane, BorderLayout.CENTER);
    }

    private JPanel statusPill() {
        JPanel panel = new GlassPanel(18);
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 14, 7));
        panel.setBorder(new EmptyBorder(0, 8, 0, 8));
        statusLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 12));
        statusLabel.setForeground(new Color(160, 229, 255));
        panel.add(statusLabel);
        return panel;
    }

    private JPanel sectionHeader(String title, String subtitle) {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 2));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 15));
        titleLabel.setForeground(TEXT_MAIN);
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        subtitleLabel.setForeground(TEXT_MUTED);
        panel.add(titleLabel);
        panel.add(subtitleLabel);
        return panel;
    }

    private JPanel titledPanel(String title, String subtitle, Component component) {
        JPanel panel = new GlassPanel(24);
        panel.setLayout(new BorderLayout(10, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        panel.add(sectionHeader(title, subtitle), BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(92, 115, 180, 90), 1));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        return scrollPane;
    }

    private void stylePrimaryButton(JButton button) {
        button.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(ACCENT_BLUE);
        button.setBorder(new EmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleSecondaryButton(JButton button) {
        button.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        button.setForeground(new Color(212, 223, 245));
        button.setBackground(new Color(33, 44, 74));
        button.setBorder(new EmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void refreshDeviceList() {
        setBusy(true, "● 正在刷新裝置清單...");
        deviceListModel.clear();
        deviceListModel.addElement("正在取得清單...");

        new Thread(() -> {
            List<String> devices = AdbUiHierarchyUtil.getAdbDevices();
            SwingUtilities.invokeLater(() -> {
                deviceListModel.clear();
                if (devices.isEmpty()) {
                    deviceListModel.addElement("[ 未偵測到裝置 ]");
                    statusLabel.setText("● 未偵測到 ADB 裝置");
                } else {
                    for (String device : devices) {
                        deviceListModel.addElement(device);
                    }
                    deviceList.setSelectedIndex(0);
                    statusLabel.setText("● 已偵測到 " + devices.size() + " 台裝置");
                }
                setBusy(false, statusLabel.getText());
            });
        }, "AdbDeviceRefresh").start();
    }

    private void dumpSelectedDeviceXml() {
        String deviceId = deviceList.getSelectedValue();
        if (deviceId == null || deviceId.startsWith("[") || deviceId.contains("取得清單")) {
            JOptionPane.showMessageDialog(this, "請先選擇一台有效裝置。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true, "● 正在讀取 " + deviceId + " 的目前畫面 XML...");
        xmlTextArea.setText("讀取中...");
        textBoundsArea.setText("解析中...");

        new Thread(() -> {
            try {
                String xml = AdbUiHierarchyUtil.getCurrentScreenXml(deviceId);
                List<AdbUiHierarchyUtil.StageNode> stageNodes = AdbUiHierarchyUtil.detectStageNodes(xml);
                String summary = formatStageNodes(stageNodes);
                SwingUtilities.invokeLater(() -> {
                    xmlTextArea.setText(xml);
                    xmlTextArea.setCaretPosition(0);
                    textBoundsArea.setText(summary);
                    textBoundsArea.setCaretPosition(0);
                    setBusy(false, "● 完成：命中 " + stageNodes.size() + " 個階段節點");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    xmlTextArea.setText("");
                    textBoundsArea.setText("讀取失敗：" + e.getMessage());
                    setBusy(false, "● 讀取失敗");
                });
            }
        }, "AdbUiHierarchyDump").start();
    }

    private void restartSelectedDeviceSolflare() {
        String deviceId = deviceList.getSelectedValue();
        if (deviceId == null || deviceId.startsWith("[") || deviceId.contains("取得清單")) {
            JOptionPane.showMessageDialog(this, "請先選擇一台有效裝置。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true, "● 正在重開 " + deviceId + " 的 Solflare...");
        new Thread(() -> {
            try {
                AdbUiHierarchyUtil.restartSolflare(deviceId);
                SwingUtilities.invokeLater(() -> setBusy(false, "● 完成：Solflare 已重新開啟"));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> setBusy(false, "● 重開 Solflare 失敗：" + e.getMessage()));
            }
        }, "AdbRestartSolflare").start();
    }

    private String formatTextBounds(List<AdbUiHierarchyUtil.TextNodeBounds> textBounds) {
        if (textBounds.isEmpty()) {
            return "目前 XML 沒有解析到 text 或 content-desc 文字節點。";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < textBounds.size(); i++) {
            AdbUiHierarchyUtil.TextNodeBounds item = textBounds.get(i);
            builder.append(String.format(
                    "%02d. text=%s | center=(%d,%d) | bounds=%s",
                    i + 1,
                    item.text(),
                    item.centerX(),
                    item.centerY(),
                    item.bounds()
            ));
            if (item.resourceId() != null && !item.resourceId().isBlank()) {
                builder.append(" | resource-id=").append(item.resourceId());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String formatResourceNodes(List<AdbUiHierarchyUtil.ResourceNode> resourceNodes) {
        if (resourceNodes.isEmpty()) {
            return "目前 XML 沒有解析到 resource-id 節點。";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < resourceNodes.size(); i++) {
            AdbUiHierarchyUtil.ResourceNode item = resourceNodes.get(i);
            builder.append(String.format(
                    "%02d. resource-id=%s | center=(%d,%d) | bounds=%s",
                    i + 1,
                    item.resourceId(),
                    item.centerX(),
                    item.centerY(),
                    item.bounds()
            ));
            if (item.text() != null && !item.text().isBlank()) {
                builder.append(" | text=").append(item.text());
            }
            if (item.contentDesc() != null && !item.contentDesc().isBlank()) {
                builder.append(" | content-desc=").append(item.contentDesc());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String formatStageNodes(List<AdbUiHierarchyUtil.StageNode> stageNodes) {
        if (stageNodes.isEmpty()) {
            return "目前 XML 沒有命中已定義階段。";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < stageNodes.size(); i++) {
            AdbUiHierarchyUtil.StageNode item = stageNodes.get(i);
            builder.append(String.format(
                    "%02d. 階段=%s | bounds=%s | center=(%d,%d)",
                    i + 1,
                    item.stage(),
                    item.bounds(),
                    item.centerX(),
                    item.centerY()
            ));
            if (item.resourceId() != null && !item.resourceId().isBlank()) {
                builder.append(" | resource-id=").append(item.resourceId());
            }
            if (item.text() != null && !item.text().isBlank()) {
                builder.append(" | text=").append(item.text());
            }
            if (item.contentDesc() != null && !item.contentDesc().isBlank()) {
                builder.append(" | content-desc=").append(item.contentDesc());
            }
            if (item.className() != null && !item.className().isBlank()) {
                builder.append(" | class=").append(item.className());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private void setBusy(boolean busy, String status) {
        refreshButton.setEnabled(!busy);
        dumpXmlButton.setEnabled(!busy);
        restartSolflareButton.setEnabled(!busy);
        statusLabel.setText(status);
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new AdbUiHierarchyViewer().setVisible(true));
    }

    private static class BackgroundPanel extends JPanel {
        BackgroundPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setPaint(new GradientPaint(0, 0, BACKGROUND_TOP, getWidth(), getHeight(), BACKGROUND_BOTTOM));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.setColor(new Color(49, 114, 255, 35));
            g2d.fillOval(-140, -120, 380, 380);
            g2d.setColor(new Color(180, 70, 255, 32));
            g2d.fillOval(getWidth() - 260, getHeight() - 260, 420, 420);
            g2d.dispose();
        }
    }

    private static class GlassPanel extends JPanel {
        private final int arc;

        GlassPanel(int arc) {
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(PANEL_BG);
            g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2d.setColor(PANEL_BORDER);
            g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2d.dispose();
            super.paintComponent(g);
        }
    }

    private static class SlimScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(86, 111, 190);
            trackColor = new Color(14, 20, 36);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }
}
