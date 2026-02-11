package seeker.plugin;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.WebElement;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.apache.commons.io.FileUtils;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Appium Device Manager
 * This class demonstrates how to identify Android devices connected via Wi-Fi ADB
 * and how to set up an Appium session for one of them.
 */
public class AppiumDeviceManager {

    /**
     * Executes 'adb devices' and filters for devices connected via Wi-Fi (IP address format).
     *
     * @return List of Wi-Fi device IDs (e.g., 192.168.1.10:5555)
     */
    public List<String> getControllableWifiDevices() {
        List<String> devices = new ArrayList<>();
        try {
            // Directly specify the path to ADB in platform-tools
            String adbPath = new java.io.File("platform-tools/adb.exe").getAbsolutePath();
            System.out.println("Using ADB at: " + adbPath);

            // Running adb devices to get the list of attached devices
            Process process = new ProcessBuilder(adbPath, "devices").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            // Regex to identify standard IP:Port OR the Android 11+ mDNS/TLS format
            // e.g., 192.168.1.10:5555 OR adb-xxx-xxx._adb-tls-connect._tcp
            Pattern wifiPattern = Pattern.compile("^(\\d{1,3}(\\.\\d{1,3}){3}:\\d+|.*\\._adb-tls-connect\\._tcp.*)");

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.endsWith("device")) {
                    String deviceId = line.split("\\s+")[0];
                    if (wifiPattern.matcher(deviceId).find()) {
                        devices.add(deviceId);
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("Error accessing ADB: " + e.getMessage());
            System.err.println("Check if ADB is at the specified path or in your system PATH.");
        }
        return devices;
    }

    /**
     * Example of how to start an Appium session and capture the screen.
     */
    public void captureDeviceScreen(String deviceId) {
        AndroidDriver driver = null;
        try {
            System.out.println("Connecting to device for screenshot: " + deviceId);

            // Get the same local ADB path to tell Appium Server where it is
            String adbPath = new java.io.File("platform-tools/adb.exe").getAbsolutePath();

            UiAutomator2Options options = new UiAutomator2Options()
                .setUdid(deviceId)
                .setPlatformName("Android")
                .setAutomationName("UiAutomator2")
                .setNoReset(true)
                .setAdbExecTimeout(Duration.ofSeconds(30));

            // Connect to Appium Server
            URL serverUrl = new URL("http://127.0.0.1:4723");
            driver = new AndroidDriver(serverUrl, options);

            // 1. Capture Screenshot (PNG Image)
            File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File destFile = new File("screenshot_" + deviceId.replaceAll("[^a-zA-Z0-9]", "_") + ".png");
            FileUtils.copyFile(srcFile, destFile);
            System.out.println("[Success] Screenshot saved to: " + destFile.getAbsolutePath());

            // 2. Capture Page Source (XML Hierarchy)
            String xmlSource = driver.getPageSource();
            File xmlFile = new File("ui_hierarchy_" + deviceId.replaceAll("[^a-zA-Z0-9]", "_") + ".xml");
            FileUtils.writeStringToFile(xmlFile, xmlSource, StandardCharsets.UTF_8);
            System.out.println("[Success] UI Hierarchy saved to: " + xmlFile.getAbsolutePath());

            // 3. Simple Parsing Example
            parseUiHierarchy(xmlFile);

            // 4. Click Element by Index (Example: Find and click element with index 11)
            clickByIndex(driver, 11);

        } catch (Exception e) {
            System.err.println("Failed to capture screen: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    /**
     * Parses the UI hierarchy XML and prints some key elements.
     */
    private void parseUiHierarchy(File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);
            
            // Get all nodes
            NodeList nodes = doc.getElementsByTagName("*");
            System.out.println("\n--- UI Elements Analysis (Sample) ---");
            
            int count = 0;
            for (int i = 0; i < nodes.getLength() && count < 15; i++) {
                Element element = (Element) nodes.item(i);
                String className = element.getAttribute("class");
                String resourceId = element.getAttribute("resource-id");
                String text = element.getAttribute("text");
                String bounds = element.getAttribute("bounds");

                // We are interested in elements that have text or a specific ID
                if (!text.isEmpty() || !resourceId.isEmpty()) {
                    System.out.printf("[%d] Class: %s\n", count + 1, className);
                    if (!resourceId.isEmpty()) System.out.println("    ID: " + resourceId);
                    if (!text.isEmpty()) System.out.println("    Text: " + text);
                    System.out.println("    Bounds: " + bounds);
                    count++;
                }
            }
            System.out.println("-------------------------------------\n");
        } catch (Exception e) {
            System.err.println("Failed to parse UI XML: " + e.getMessage());
        }
    }

    /**
     * Finds an element by index attribute and clicks it.
     * Note: 'index' is usually relative to the parent node.
     */
    public void clickByIndex(AndroidDriver driver, int targetIndex) {
        try {
            System.out.println("Attempting to click element with index: " + targetIndex);
            
            // Way 1: Use UiSelector (Most common for Android index)
            WebElement element = driver.findElement(AppiumBy.androidUIAutomator(
                "new UiSelector().index(" + targetIndex + ")"
            ));
            
            // Way 2: Use XPath (Optional fallback)
            // WebElement element = driver.findElement(AppiumBy.xpath("//*[@index='" + targetIndex + "']"));

            element.click();
            System.out.println("[Success] Clicked element index " + targetIndex);
        } catch (Exception e) {
            System.err.println("Failed to click element: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        AppiumDeviceManager manager = new AppiumDeviceManager();

        System.out.println("Searching for Wi-Fi controllable devices...");
        List<String> wifiDevices = manager.getControllableWifiDevices();

        if (wifiDevices.isEmpty()) {
            System.out.println("\n[!] No Wi-Fi devices found.");
            System.out.println("To connect a device via Wi-Fi, ensure it's on the same network and run:");
            System.out.println("   adb connect <device_ip>:5555");
        } else {
            System.out.println("\n[+] Found " + wifiDevices.size() + " Wi-Fi device(s):");
            for (String id : wifiDevices) {
                System.out.println("    - " + id);
            }

            // Example: Capture screen for the first found device
            manager.captureDeviceScreen(wifiDevices.get(0));
        }
    }
}
