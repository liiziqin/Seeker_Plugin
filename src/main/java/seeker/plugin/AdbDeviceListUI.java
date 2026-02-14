package seeker.plugin;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AdbDeviceListUI extends JFrame {
    private JTextArea deviceListArea;
    private JButton refreshButton;

    public AdbDeviceListUI() {
        setTitle("ADB Device List");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        refreshDeviceList();
    }

    private void initComponents() {
        deviceListArea = new JTextArea();
        deviceListArea.setEditable(false);
        deviceListArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(deviceListArea);

        refreshButton = new JButton("刷新裝置清單");
        refreshButton.addActionListener(e -> refreshDeviceList());

        setLayout(new BorderLayout());
        add(new JLabel(" 連線中的 ADB 裝置：", JLabel.LEFT), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(refreshButton, BorderLayout.SOUTH);
    }

    private void refreshDeviceList() {
        deviceListArea.setText(" 正在獲取清單...\n");

        new Thread(() -> {
            List<String> devices = getAdbDevices();
            SwingUtilities.invokeLater(() -> {
                deviceListArea.setText("");
                if (devices.isEmpty()) {
                    deviceListArea.append(" 未發現任何連線裝置。\n");
                } else {
                    for (String device : devices) {
                        deviceListArea.append(" • " + device + "\n");
                    }
                }
            });
        }).start();
    }

    private List<String> getAdbDevices() {
        List<String> devices = new ArrayList<>();
        try {
            // 取得專案根目錄 (假設在 Seeker_Plugin 下執行)
            String projectRoot = System.getProperty("user.dir");
            String adbPath = projectRoot + File.separator + "platform-tools" + File.separator + "adb.exe";

            // 如果上述路徑找不到，嘗試檢查 PATH
            File adbFile = new File(adbPath);
            String command = adbFile.exists() ? adbFile.getAbsolutePath() : "adb";

            ProcessBuilder pb = new ProcessBuilder(command, "devices");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                boolean foundHeader = false;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty())
                        continue;

                    if (line.startsWith("List of devices attached")) {
                        foundHeader = true;
                        continue;
                    }

                    if (foundHeader && line.contains("device")) {
                        String deviceId = line.split("\\s+")[0];
                        devices.add(deviceId);
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            devices.add("錯誤: " + e.getMessage());
        }
        return devices;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new AdbDeviceListUI().setVisible(true);
        });
    }
}
