package seeker.plugin;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.event.*;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.JComponent;

public class Seeker_Plugin {
    private JPanel mainPanel;
    private JList<String> deviceList;
    private JButton refreshButton;
    private JButton recordButton; // This reflects the '執行' button in the form
    private DefaultListModel<String> listModel;

    public Seeker_Plugin() {
        listModel = new DefaultListModel<>();
        deviceList.setModel(listModel);

        refreshButton.addActionListener(e -> refreshDeviceList());
        recordButton.addActionListener(e -> recordSelectedDevice());
        
        // 新增功能：按下 Ctrl+M 開啟滑鼠工具 (或提示可以使用 MouseTrackerTool)
        mainPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control M"), "openMouseTool");
        mainPanel.getActionMap().put("openMouseTool", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                new Thread(() -> {
                    com.formdev.flatlaf.FlatDarkLaf.setup();
                    SwingUtilities.invokeLater(() -> new MouseTrackerTool().setVisible(true));
                }).start();
            }
        });

        refreshDeviceList();
    }

    private void refreshDeviceList() {
        listModel.clear();
        listModel.addElement(" 正在獲取清單...");
        new Thread(() -> {
            List<String> devices = getAdbDevices();
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                if (devices.isEmpty()) {
                    listModel.addElement(" 未發現任何連線裝置。");
                } else {
                    for (String device : devices) {
                        listModel.addElement(device);
                    }
                }
            });
        }).start();
    }

    private void recordSelectedDevice() {
        String selectedDevice = deviceList.getSelectedValue();
        if (selectedDevice == null || selectedDevice.startsWith(" ") || selectedDevice.contains("未發現")) {
            JOptionPane.showMessageDialog(mainPanel, "請先從清單中選擇一個有效的裝置！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            DeviceUtil.recordDeviceState(selectedDevice);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(mainPanel, "已成功將 " + selectedDevice + " 的資訊記錄在根目錄下 Records 資料夾！", "完成",
                        JOptionPane.INFORMATION_MESSAGE);
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
        frame.setSize(500, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
