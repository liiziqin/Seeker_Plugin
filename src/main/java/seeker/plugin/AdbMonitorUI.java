package seeker.plugin;

import com.formdev.flatlaf.FlatDarkLaf;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ADB 裝置監控與自動化工具
 * 支持全域熱鍵 F1 切換對所有裝置的循環點擊操作
 */
public class AdbMonitorUI extends JFrame implements NativeKeyListener {
    private DefaultListModel<String> deviceListModel;
    private JList<String> deviceList;
    private JCheckBox realTimeUpdateCheckBox;
    private Timer updateTimer;
    private JLabel statusLabel;
    private JButton refreshButton;
    private JButton automationButton;

    private boolean isLooping = false;
    private Thread loopThread;
    private final Random random = new Random();
    private String adbPath = "adb";

    public AdbMonitorUI() {
        detectAdbPath();
        initUI();
        initGlobalHook();
        startMonitoring();
    }

    private void detectAdbPath() {
        String projectRoot = System.getProperty("user.dir");
        String potentialPath = projectRoot + File.separator + "platform-tools" + File.separator + "adb.exe";
        if (new File(potentialPath).exists()) {
            adbPath = potentialPath;
        }
    }

    private void initUI() {
        setTitle("Seeker - ADB 自動化監控工具");
        setSize(450, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        // 主面板 - 漸層背景
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(43, 45, 48), 0, getHeight(), new Color(25, 26, 28));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setLayout(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 頂部導航
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel("裝置控制中心");
        titleLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 20));
        titleLabel.setForeground(new Color(88, 166, 255));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        realTimeUpdateCheckBox = new JCheckBox("列表實時更新");
        realTimeUpdateCheckBox.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        realTimeUpdateCheckBox.setForeground(new Color(201, 209, 217));
        realTimeUpdateCheckBox.setOpaque(false);
        realTimeUpdateCheckBox.setFocusable(false);
        realTimeUpdateCheckBox.addActionListener(e -> toggleUpdateTimer());
        headerPanel.add(realTimeUpdateCheckBox, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // 中間列表 (JList)
        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);
        deviceList.setFont(new Font("Consolas", Font.PLAIN, 15));
        deviceList.setBackground(new Color(22, 23, 26));
        deviceList.setForeground(new Color(200, 200, 200));
        deviceList.setSelectionBackground(new Color(33, 110, 214));
        deviceList.setFixedCellHeight(35);
        deviceList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(0, 10, 0, 10));
                return label;
            }
        });

        JScrollPane scrollPane = new JScrollPane(deviceList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 63, 65)));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部控制
        JPanel footerPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        footerPanel.setOpaque(false);

        // 狀態與刷新
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusLabel = new JLabel("● 準備就緒 [F1 切換自動化]");
        statusLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(110, 118, 129));
        statusRow.add(statusLabel, BorderLayout.WEST);

        refreshButton = new JButton("手動刷新清單");
        refreshButton.setFocusable(false);
        refreshButton.addActionListener(e -> refreshDeviceList());
        statusRow.add(refreshButton, BorderLayout.EAST);

        // 自動化大按鈕
        automationButton = new JButton("開始自動化迴圈 (F1)");
        automationButton.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        automationButton.setBackground(new Color(46, 160, 67));
        automationButton.setForeground(Color.WHITE);
        automationButton.setFocusable(false);
        automationButton.addActionListener(e -> toggleAutomation());

        footerPanel.add(statusRow);
        footerPanel.add(automationButton);

        mainPanel.add(footerPanel, BorderLayout.SOUTH);
        add(mainPanel);

        updateTimer = new Timer(3000, e -> refreshDeviceList());
    }

    private void initGlobalHook() {
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
        } catch (NativeHookException ex) {
            System.err.println("Could not register native hook: " + ex.getMessage());
        }
    }

    private void toggleUpdateTimer() {
        if (realTimeUpdateCheckBox.isSelected()) updateTimer.start();
        else updateTimer.stop();
    }

    private void toggleAutomation() {
        isLooping = !isLooping;
        if (isLooping) {
            automationButton.setText("停止自動化迴圈 (F1)");
            automationButton.setBackground(new Color(207, 34, 46));
            statusLabel.setText("● 運作中...");
            statusLabel.setForeground(new Color(46, 160, 67));
            startAutomationLoop();
        } else {
            automationButton.setText("開始自動化迴圈 (F1)");
            automationButton.setBackground(new Color(46, 160, 67));
            statusLabel.setText("● 已停止");
            statusLabel.setForeground(new Color(207, 34, 46));
            if (loopThread != null) loopThread.interrupt();
        }
    }

    private void startAutomationLoop() {
        loopThread = new Thread(() -> {
            try {
                while (isLooping) {
                    List<String> devices = getAdbDevices();
                    if (devices.isEmpty()) {
                        SwingUtilities.invokeLater(() -> statusLabel.setText("● 錯誤：未偵測到裝置"));
                        Thread.sleep(3000);
                        continue;
                    }

                    for (String deviceId : devices) {
                        if (!isLooping) break;
                        
                        SwingUtilities.invokeLater(() -> statusLabel.setText("● 執行中: " + deviceId));

                        // 1. 隨機幣種 (600, 1160)
                        if (random.nextInt(100) < 50) { // 50% 機率
                            tap(deviceId, 600, 1160);
                        }
                        Thread.sleep(1000);

                        // 3. 隨機 25% (900, 1060) 或 50% (770, 1060)
                        if (random.nextBoolean()) {
                            tap(deviceId, 900, 1060);
                        } else {
                            tap(deviceId, 770, 1060);
                        }
                        Thread.sleep(2000);

                        // 5. Swap (600, 1850)
                        tap(deviceId, 600, 1850);
                        Thread.sleep(2000);

                        // 7. Approve (600, 2440) - 連續點擊 12 次
                        for (int i = 0; i < 12; i++) {
                            tap(deviceId, 600, 2440);
                            Thread.sleep(10); // 微小間隔確保手機捕捉到
                        }
                        Thread.sleep(3000);
                    }
                }
            } catch (InterruptedException e) {
                // Thread stopped
            }
        });
        loopThread.start();
    }

    private void tap(String deviceId, int x, int y) {
        // 隨機偏置 +-3
        int rx = x + random.nextInt(7) - 3;
        int ry = y + random.nextInt(7) - 3;
        
        String command = String.format("\"%s\" -s %s shell input tap %d %d", adbPath, deviceId, rx, ry);
        try {
            Runtime.getRuntime().exec(command);
            System.out.println("Execute: " + command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshDeviceList() {
        new Thread(() -> {
            List<String> devices = getAdbDevices();
            SwingUtilities.invokeLater(() -> {
                deviceListModel.clear();
                for (String d : devices) deviceListModel.addElement(d);
                if (devices.isEmpty()) deviceListModel.addElement(" [ 未偵測到裝置 ] ");
            });
        }).start();
    }

    private List<String> getAdbDevices() {
        List<String> devices = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec("\"" + adbPath + "\" devices");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean headerPassed = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("List of devices attached")) {
                    headerPassed = true;
                    continue;
                }
                if (headerPassed && line.contains("\tdevice")) {
                    devices.add(line.split("\t")[0]);
                }
            }
        } catch (Exception e) {
            System.err.println("ADB Read Error: " + e.getMessage());
        }
        return devices;
    }

    private void startMonitoring() {
        refreshDeviceList();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (e.getKeyCode() == NativeKeyEvent.VC_F1) {
            SwingUtilities.invokeLater(this::toggleAutomation);
        }
    }

    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new AdbMonitorUI().setVisible(true));
    }
}
