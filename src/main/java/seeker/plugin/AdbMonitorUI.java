package seeker.plugin;

import com.formdev.flatlaf.FlatDarkLaf;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
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
    private JCheckBox keepAwakeCheckBox;
    private Timer updateTimer;
    private JLabel statusLabel;
    private JButton refreshButton;
    private JButton automationButton;
    private JButton autoRestartBackPackBtn;
    private JSpinner timeSpinner;
    private JToggleButton autoSwitchBtn;
    private Timer schedulerTimer;

    /**
     * Windows 原生 API 介面定義
     */
    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        // ES_CONTINUOUS: 通知系統目前狀態應持續直到下次呼叫
        long ES_CONTINUOUS = 0x80000000L;
        // ES_SYSTEM_REQUIRED: 防止系統進入睡眠模式
        long ES_SYSTEM_REQUIRED = 0x00000001L;
        // ES_DISPLAY_REQUIRED: 防止螢幕關閉
        long ES_DISPLAY_REQUIRED = 0x00000002L;

        long SetThreadExecutionState(long esFlags);
    }

    private volatile boolean isLooping = false;
    private List<Thread> deviceThreads = new ArrayList<>();
    private volatile boolean isBackPackLooping = false;
    private List<Thread> backPackThreads = new ArrayList<>();
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
        setSize(450, 650);
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
                GradientPaint gp = new GradientPaint(0, 0, new Color(43, 45, 48), 0, getHeight(),
                        new Color(25, 26, 28));
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

        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        configPanel.setOpaque(false);

        keepAwakeCheckBox = new JCheckBox("螢幕常亮");
        keepAwakeCheckBox.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        keepAwakeCheckBox.setForeground(new Color(201, 209, 217));
        keepAwakeCheckBox.setOpaque(false);
        keepAwakeCheckBox.setFocusable(false);
        keepAwakeCheckBox.addActionListener(e -> toggleStayAwake());

        realTimeUpdateCheckBox = new JCheckBox("列表實時更新");
        realTimeUpdateCheckBox.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        realTimeUpdateCheckBox.setForeground(new Color(201, 209, 217));
        realTimeUpdateCheckBox.setOpaque(false);
        realTimeUpdateCheckBox.setFocusable(false);
        realTimeUpdateCheckBox.addActionListener(e -> toggleUpdateTimer());

        configPanel.add(keepAwakeCheckBox);
        configPanel.add(realTimeUpdateCheckBox);
        headerPanel.add(configPanel, BorderLayout.EAST);

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
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                label.setBorder(new EmptyBorder(0, 10, 0, 10));
                return label;
            }
        });

        JScrollPane scrollPane = new JScrollPane(deviceList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 63, 65)));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部控制
        JPanel footerPanel = new JPanel(new GridLayout(5, 1, 10, 10));
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

        // 亮度控制快捷按鈕
        JPanel brightnessPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        brightnessPanel.setOpaque(false);

        JButton lowBrightnessBtn = new JButton("手動模式 (亮度1)");
        lowBrightnessBtn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        lowBrightnessBtn.setFocusable(false);
        lowBrightnessBtn.setBackground(new Color(60, 63, 65));
        lowBrightnessBtn.setForeground(Color.WHITE);
        lowBrightnessBtn.addActionListener(e -> setAllDevicesBrightness(0, 1));

        JButton highBrightnessBtn = new JButton("自動模式 (亮度20)");
        highBrightnessBtn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        highBrightnessBtn.setFocusable(false);
        highBrightnessBtn.setBackground(new Color(33, 110, 214));
        highBrightnessBtn.setForeground(Color.WHITE);
        highBrightnessBtn.addActionListener(e -> setAllDevicesBrightness(1, 20));

        brightnessPanel.add(lowBrightnessBtn);
        brightnessPanel.add(highBrightnessBtn);

        // BackPack 控制面板
        JPanel backpackPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        backpackPanel.setOpaque(false);

        autoRestartBackPackBtn = new JButton("自動重啟BackPack");
        autoRestartBackPackBtn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        autoRestartBackPackBtn.setFocusable(false);
        autoRestartBackPackBtn.setBackground(new Color(133, 60, 214));
        autoRestartBackPackBtn.setForeground(Color.WHITE);
        autoRestartBackPackBtn.addActionListener(e -> toggleAutoRestartBackPack());

        JButton resetBackPackBtn = new JButton("重置BackPack");
        resetBackPackBtn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        resetBackPackBtn.setFocusable(false);
        resetBackPackBtn.setBackground(new Color(214, 110, 33));
        resetBackPackBtn.setForeground(Color.WHITE);
        resetBackPackBtn.addActionListener(e -> resetBackPackAllDevices());

        backpackPanel.add(autoRestartBackPackBtn);
        backpackPanel.add(resetBackPackBtn);

        // 自動化大按鈕
        automationButton = new JButton("開始自動化迴圈 (F1)");
        automationButton.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        automationButton.setBackground(new Color(46, 160, 67));
        automationButton.setForeground(Color.WHITE);
        automationButton.setFocusable(false);
        automationButton.addActionListener(e -> toggleAutomation());

        // 排程控制
        JPanel schedulerPanel = new JPanel(new BorderLayout(10, 0));
        schedulerPanel.setOpaque(false);

        SpinnerDateModel dateModel = new SpinnerDateModel();
        timeSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(timeSpinner, "yyyy-MM-dd HH:mm:ss");
        timeSpinner.setEditor(timeEditor);
        timeSpinner.setFont(new Font("Consolas", Font.PLAIN, 14));
        timeSpinner.setFocusable(false);

        autoSwitchBtn = new JToggleButton("時間到自動切換");
        autoSwitchBtn.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        autoSwitchBtn.setBackground(new Color(60, 63, 65));
        autoSwitchBtn.setForeground(Color.WHITE);
        autoSwitchBtn.addActionListener(e -> toggleAutoSwitch());

        schedulerPanel.add(timeSpinner, BorderLayout.CENTER);
        schedulerPanel.add(autoSwitchBtn, BorderLayout.EAST);

        footerPanel.add(statusRow);
        footerPanel.add(brightnessPanel);
        footerPanel.add(backpackPanel);
        footerPanel.add(schedulerPanel);
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
        if (realTimeUpdateCheckBox.isSelected())
            updateTimer.start();
        else
            updateTimer.stop();
    }

    /**
     * 使用 Windows 原生 API 切換防止睡眠狀態
     */
    private void toggleStayAwake() {
        if (keepAwakeCheckBox.isSelected()) {
            // 請求系統保持開啟狀態：持續性 | 系統喚醒 | 螢幕開啟
            Kernel32.INSTANCE.SetThreadExecutionState(
                    Kernel32.ES_CONTINUOUS | Kernel32.ES_SYSTEM_REQUIRED | Kernel32.ES_DISPLAY_REQUIRED);
            System.out.println("Windows API: 已註冊保持喚醒狀態");
        } else {
            // 恢復系統正常電源設定
            Kernel32.INSTANCE.SetThreadExecutionState(Kernel32.ES_CONTINUOUS);
            System.out.println("Windows API: 已恢復正常電源設定");
        }
    }

    private void toggleAutoSwitch() {
        if (autoSwitchBtn.isSelected()) {
            try {
                // 強制提交編輯，確保 Model 獲取到最新輸入的文字
                timeSpinner.commitEdit();
            } catch (Exception e) {
                // 如果格式錯誤，可維持舊值或報錯
            }
            autoSwitchBtn.setBackground(new Color(214, 150, 33));
            if (schedulerTimer == null) {
                schedulerTimer = new Timer(1000, e -> checkSchedule());
            }
            schedulerTimer.start();
        } else {
            autoSwitchBtn.setBackground(new Color(60, 63, 65));
            if (schedulerTimer != null) {
                schedulerTimer.stop();
            }
        }
    }

    private void checkSchedule() {
        Date selectedDate = (Date) timeSpinner.getValue();
        if (new Date().after(selectedDate)) {
            autoSwitchBtn.setSelected(false);
            toggleAutoSwitch(); // 停止計時器並重置樣式

            // 當目前日期時間超過選擇的日期時間，停止`自動化迴圈`、開啟`自動重啟BackPack`
            if (isLooping) {
                toggleAutomation();
            }
            if (!isBackPackLooping) {
                toggleAutoRestartBackPack();
            }
        }
    }

    private void toggleAutomation() {
        isLooping = !isLooping;
        if (isLooping) {
            setAllDevicesBrightness(0, 1);
            automationButton.setText("停止自動化迴圈 (F1)");
            automationButton.setBackground(new Color(207, 34, 46));
            statusLabel.setText("● 運作中...");
            statusLabel.setForeground(new Color(46, 160, 67));
            startAutomationLoop();
        } else {
            setAllDevicesBrightness(1, 20);
            automationButton.setText("開始自動化迴圈 (F1)");
            automationButton.setBackground(new Color(46, 160, 67));
            statusLabel.setText("● 已停止");
            statusLabel.setForeground(new Color(207, 34, 46));
            if (deviceThreads != null) {
                for (Thread t : deviceThreads)
                    t.interrupt();
                deviceThreads.clear();
            }
        }
    }

    private void startAutomationLoop() {
        new Thread(() -> {
            List<String> devices = getAdbDevices();
            if (devices.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("● 錯誤：未偵測到裝置");
                    toggleAutomation(); // 重置按鈕狀態
                });
                return;
            }

            deviceThreads.clear();
            for (String deviceId : devices) {
                Thread t = new Thread(() -> {
                    try {
                        System.out.println("Started thread for device: " + deviceId);
                        while (isLooping) {
                            runDeviceSequence(deviceId);
                        }
                    } catch (InterruptedException e) {
                        System.out.println("Thread interrupted for device: " + deviceId);
                    } finally {
                        System.out.println("Thread finished for device: " + deviceId);
                    }
                }, "Thread-" + deviceId);
                deviceThreads.add(t);
                t.start();
            }

            SwingUtilities.invokeLater(() -> statusLabel.setText("● " + devices.size() + " 台裝置運行中..."));
        }).start();
    }

    private void runDeviceSequence(String deviceId) throws InterruptedException {
        // 關閉交易成功視窗
        for (int i = 0; i < 3; ++i) {
            tap(deviceId, 1115, 570);
            Thread.sleep(300);
        }

        // 0. 刷新(1113, 606)
        tap(deviceId, 1115, 600);
        Thread.sleep(1000);

        // 1. 隨機幣種 (600, 1160)
        if (random.nextInt(100) < 50) { // 50% 機率
            tap(deviceId, 600, 1160);
        }
        Thread.sleep(1000);
        if (!isLooping)
            return;

        // 3. 隨機 25% (900, 1060) 或 50% (770, 1060)
        if (random.nextBoolean()) {
            tap(deviceId, 900, 1060);
        } else {
            tap(deviceId, 770, 1060);
        }
        Thread.sleep(2000);
        if (!isLooping)
            return;

        // 5. Swap (600, 1850)
        tap(deviceId, 600, 1850);
        Thread.sleep(2000);
        if (!isLooping)
            return;

        // 7. Approve (600, 2440) - 連續點擊 20 次
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 20; j++) {
                if (!isLooping)
                    return;
                tap(deviceId, 600, 2440);
                Thread.sleep(10); // 微小間隔
            }
            Thread.sleep(1500);
        }
        Thread.sleep(1000);
    }

    private void tap(String deviceId, int x, int y) {
        // 隨機偏置 +-3
        int rx = x + random.nextInt(7) - 3;
        int ry = y + random.nextInt(7) - 3;
        runAdb(deviceId, String.format("shell input tap %d %d", rx, ry));
    }

    private void toggleAutoRestartBackPack() {
        isBackPackLooping = !isBackPackLooping;
        if (isBackPackLooping) {
            setAllDevicesBrightness(0, 1);
            autoRestartBackPackBtn.setText("停止重啟BackPack");
            autoRestartBackPackBtn.setBackground(new Color(207, 34, 46));
            startAutoRestartBackPackLoop();
        } else {
            setAllDevicesBrightness(1, 20);
            autoRestartBackPackBtn.setText("自動重啟BackPack");
            autoRestartBackPackBtn.setBackground(new Color(133, 60, 214));
            if (backPackThreads != null) {
                for (Thread t : backPackThreads)
                    t.interrupt();
                backPackThreads.clear();
            }
        }
    }

    private void startAutoRestartBackPackLoop() {
        new Thread(() -> {
            List<String> devices = getAdbDevices();
            if (devices.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("● 錯誤：未偵測到裝置");
                    toggleAutoRestartBackPack();
                });
                return;
            }

            backPackThreads.clear();
            for (String deviceId : devices) {
                Thread t = new Thread(() -> {
                    try {
                        System.out.println("Started BackPack loop for: " + deviceId);
                        while (isBackPackLooping) {
                            runAdb(deviceId,
                                    "shell am start -n app.backpack.mobile.standalone/app.backpack.mobile.standalone.MainActivity");
                            Thread.sleep(5000);
                            if (!isBackPackLooping)
                                break;

                            int iterations = 5 + random.nextInt(11); // 隨機 5 到 15 次
                            for (int i = 0; i < iterations; i++) {
                                if (!isBackPackLooping)
                                    break;

                                int type = random.nextInt(3); // 隨機選擇三種滑動方式之一
                                int rx1, ry1, rx2, ry2;

                                if (type == 0) {
                                    // 原有的垂直滑動 (瀏覽)
                                    rx1 = 600 + random.nextInt(11) - 5;
                                    ry1 = 1550 + random.nextInt(11) - 5;
                                    rx2 = 600 + random.nextInt(11) - 5;
                                    ry2 = 2350 + random.nextInt(11) - 5;
                                } else if (type == 1) {
                                    // 新增橫向滑動 1: 200,600 -> 1000,600
                                    rx1 = 200 + random.nextInt(11) - 5;
                                    ry1 = 600 + random.nextInt(11) - 5;
                                    rx2 = 1000 + random.nextInt(11) - 5;
                                    ry2 = 600 + random.nextInt(11) - 5;
                                } else {
                                    // 新增橫向滑動 2: 1000,600 -> 200,600
                                    rx1 = 1000 + random.nextInt(11) - 5;
                                    ry1 = 600 + random.nextInt(11) - 5;
                                    rx2 = 200 + random.nextInt(11) - 5;
                                    ry2 = 600 + random.nextInt(11) - 5;
                                }

                                runAdb(deviceId,
                                        String.format("shell input swipe %d %d %d %d 500", rx1, ry1, rx2, ry2));
                                Thread.sleep(3000);
                            }
                            if (!isBackPackLooping)
                                break;

                            runAdb(deviceId, "shell am force-stop app.backpack.mobile.standalone");
                            // 等待一段時間再重新開啟
                            Thread.sleep(2000);
                        }
                    } catch (InterruptedException e) {
                        System.out.println("BackPack thread interrupted for: " + deviceId);
                    }
                }, "BackPack-" + deviceId);
                backPackThreads.add(t);
                t.start();
            }
            SwingUtilities.invokeLater(() -> statusLabel.setText("● BackPack 重啟迴圈運行中 (" + devices.size() + "台)"));
        }).start();
    }

    private void resetBackPackAllDevices() {
        new Thread(() -> {
            List<String> devices = getAdbDevices();
            for (String deviceId : devices) {
                runAdb(deviceId, "shell pm clear app.backpack.mobile.standalone");
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                runAdb(deviceId,
                        "shell am start -n app.backpack.mobile.standalone/app.backpack.mobile.standalone.MainActivity");
            }
        }).start();
    }

    private void setAllDevicesBrightness(int mode, int value) {
        new Thread(() -> {
            List<String> devices = getAdbDevices();
            for (String deviceId : devices) {
                runAdb(deviceId, "shell settings put system screen_brightness_mode " + mode);
                runAdb(deviceId, "shell settings put system screen_brightness " + value);
            }
        }).start();
    }

    private void runAdb(String deviceId, String shellCommand) {
        String command = String.format("\"%s\" -s %s %s", adbPath, deviceId, shellCommand);
        try {
            Runtime.getRuntime().exec(command);
            System.out.println("Execute: " + command);
        } catch (Exception e) {
            System.err.println("ADB Execute Error: " + e.getMessage());
        }
    }

    private void refreshDeviceList() {
        new Thread(() -> {
            List<String> devices = getAdbDevices();
            SwingUtilities.invokeLater(() -> {
                deviceListModel.clear();
                for (String d : devices)
                    deviceListModel.addElement(d);
                if (devices.isEmpty())
                    deviceListModel.addElement(" [ 未偵測到裝置 ] ");
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

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new AdbMonitorUI().setVisible(true));
    }
}
