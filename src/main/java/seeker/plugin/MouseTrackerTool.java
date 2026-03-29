package seeker.plugin;

import com.formdev.flatlaf.FlatDarkLaf;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 鼠標追蹤工具 - 即時顯示座標並支持全域熱鍵點擊
 */
public class MouseTrackerTool extends JFrame implements NativeKeyListener {
    private JLabel coordLabel;
    private JLabel hotkeyLabel;
    private Robot robot;
    private int hotkeyCodeBase = NativeKeyEvent.VC_BACKQUOTE;
    private boolean isHotkeyActive = true;
    private JButton toggleButton;
    private Random random = new Random();

    // 特定座標任務
    private final int TARGET_X = 260;
    private final int TARGET_Y = 530;
    private final int CLICK_COUNT = 20;

    public MouseTrackerTool() {
        initUI();
        initRobot();
        initHook();
        startTracking();
    }

    private void initUI() {
        setTitle("Seeker - 鼠標追蹤工具");
        setSize(320, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setAlwaysOnTop(true); // 始終置頂，方便觀察
        setResizable(false);
        setLayout(new BorderLayout());

        // 背景與佈局面板 (現代化漸層美化)
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 漸層背景：深灰到深藍
                GradientPaint gp = new GradientPaint(0, 0, new Color(43, 45, 48), 0, getHeight(), new Color(30, 31, 34));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());

                // 畫一個微弱的外框
                g2d.setColor(new Color(60, 63, 65));
                g2d.drawRect(5, 5, getWidth() - 11, getHeight() - 11);
            }
        };
        mainPanel.setLayout(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1;

        // 標題標籤
        JLabel header = new JLabel("當前滑鼠位置", SwingConstants.CENTER);
        header.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        header.setForeground(new Color(88, 166, 255)); // 質感亮藍
        gbc.gridy = 0;
        mainPanel.add(header, gbc);

        // 座標顯示標籤 (大字體、多行顯示)
        coordLabel = new JLabel("<html>X: 0<br/>Y: 0</html>", SwingConstants.CENTER);
        coordLabel.setFont(new Font("Consolas", Font.BOLD, 42));
        coordLabel.setForeground(new Color(230, 237, 243));
        gbc.gridy = 1;
        gbc.insets = new Insets(15, 0, 15, 0);
        mainPanel.add(coordLabel, gbc);

        // 熱鍵提示標籤 (改為多行顯示所有任務)
        hotkeyLabel = new JLabel("<html><div style='text-align: center; color: #6e7681;'>" +
                "[ ` ] 執行核准 (15次點擊)<br/>" +
                "[ 1 ] (265, 305) 點擊 1 次<br/>" +
                "[ 2 ] (300, 285) 或 (320, 285) 隨機點擊<br/>" +
                "[ 3 ] (275, 430) 點擊 1 次" +
                "</div></html>", SwingConstants.CENTER);
        hotkeyLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        gbc.gridy = 2;
        mainPanel.add(hotkeyLabel, gbc);

        // 狀態指示燈
        JLabel statusLabel = new JLabel("● 實時連線中", SwingConstants.LEFT);
        statusLabel.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 10));
        statusLabel.setForeground(new Color(46, 160, 67)); // 綠色正常
        gbc.gridy = 3;
        gbc.insets = new Insets(10, 0, 5, 0);
        mainPanel.add(statusLabel, gbc);

        // 切換按鈕：重新命名為「核准模式」
        toggleButton = new JButton("核准功能：生效中");
        toggleButton.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        toggleButton.setFocusable(false);
        toggleButton.setBackground(new Color(207, 34, 46)); // 紅色表示點擊動作為「禁用」
        toggleButton.setForeground(Color.WHITE);
        toggleButton.addActionListener(e -> toggleHotkey());
        gbc.gridy = 4;
        gbc.insets = new Insets(10, 0, 0, 0);
        mainPanel.add(toggleButton, gbc);

        add(mainPanel, BorderLayout.CENTER);

        // 讓視窗顯示在螢幕中央偏上的位置
        setLocationRelativeTo(null);
    }

    private void initRobot() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    private void toggleHotkey() {
        isHotkeyActive = !isHotkeyActive;
        if (isHotkeyActive) {
            toggleButton.setText("自動化功能：生效中");
            toggleButton.setBackground(new Color(207, 34, 46));
            hotkeyLabel.setForeground(new Color(110, 118, 129));
        } else {
            toggleButton.setText("自動化功能：已停止");
            toggleButton.setBackground(new Color(46, 160, 67));
            hotkeyLabel.setForeground(new Color(207, 34, 46));
        }
    }

    private void initHook() {
        // 關閉 JNativeHook 的過多日誌
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
        } catch (NativeHookException ex) {
            System.err.println("無法註冊 Native Hook: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "無法攔截熱鍵，請確認權限。\n" + ex.getMessage(), "錯誤", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startTracking() {
        // 使用 Swing Timer 每 50 毫秒更新一次座標，不會造成過大負擔
        Timer timer = new Timer(50, e -> {
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if (pointerInfo != null) {
                Point p = pointerInfo.getLocation();
                coordLabel.setText(String.format("<html>X: %d<br/>Y: %d</html>", p.x, p.y));
            }
        });
        timer.start();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (!isHotkeyActive || robot == null) return;

        int code = e.getKeyCode();

        // 任務 ` : 原有的核准功能 (15次點擊)
        if (code == NativeKeyEvent.VC_BACKQUOTE) {
            runTask("執行核准", () -> performClicks(TARGET_X, TARGET_Y, CLICK_COUNT, 10));
        }
        // 任務 1 : (265, 305) 點擊 1 次
        else if (code == NativeKeyEvent.VC_1) {
            runTask("任務 1", () -> performClicks(265, 305, 1, 0));
        }
        // 任務 2 : (300, 285) 或 (320, 285) 隨機取一個座標點擊
        else if (code == NativeKeyEvent.VC_2) {
            boolean pickFirst = random.nextBoolean();
            int x = pickFirst ? 300 : 320;
            int y = 285;
            runTask("任務 2 (隨機)", () -> performClicks(x, y, 1, 0));
        }
        // 任務 3 : (275, 430) 點擊一次
        else if (code == NativeKeyEvent.VC_3) {
            runTask("任務 3", () -> performClicks(275, 430, 1, 0));
        }
    }

    private void runTask(String taskName, Runnable taskLogic) {
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                hotkeyLabel.setText("<html><div style='text-align: center; color: #ffab00;'>正在執行: " + taskName + "</div></html>");
            });

            taskLogic.run();

            SwingUtilities.invokeLater(() -> {
                Timer resetTimer = new Timer(800, x -> {
                    hotkeyLabel.setText("<html><div style='text-align: center; color: #6e7681;'>" +
                            "[ ` ] 執行核准 (15次點擊)<br/>" +
                            "[ 1 ] (265, 305) 點擊 1 次<br/>" +
                            "[ 2 ] (300, 285) 或 (320, 285) 隨機點擊<br/>" +
                            "[ 3 ] (275, 430) 點擊 1 次" +
                            "</div></html>");
                });
                resetTimer.setRepeats(false);
                resetTimer.start();
            });
        }).start();
    }

    private void performClicks(int x, int y, int count, int delayMs) {
        for (int i = 0; i < count; i++) {
            // 加入隨機座標偏移 +-3 (模擬人為點擊的不精確性)
            int offsetX = random.nextInt(7) - 3; // -3, -2, -1, 0, 1, 2, 3
            int offsetY = random.nextInt(7) - 3;

            robot.mouseMove(x + offsetX, y + offsetY);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            if (delayMs > 0) robot.delay(delayMs);
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    public static void main(String[] args) {
        // 設定美化外觀
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            MouseTrackerTool tool = new MouseTrackerTool();
            tool.setVisible(true);
        });
    }
}
