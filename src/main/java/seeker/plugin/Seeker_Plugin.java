package seeker.plugin;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Seeker_Plugin {
    private JPanel mainPanel;
    private JTextArea deviceListArea;
    private JButton refreshButton;

    public Seeker_Plugin() {
        refreshButton.addActionListener(e -> refreshDeviceList());
        refreshDeviceList();
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
            String projectRoot = System.getProperty("user.dir");
            String adbPath = projectRoot + File.separator + "platform-tools" + File.separator + "adb.exe";
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
        JFrame frame = new JFrame("Seeker_Plugin");
        frame.setContentPane(new Seeker_Plugin().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
