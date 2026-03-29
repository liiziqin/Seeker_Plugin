package seeker.plugin;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

public class DeviceUtil {

    public static void recordDeviceState(String deviceId) {
        AndroidDriver driver = null;
        try {
            // Appium setup
            String projectRoot = System.getProperty("user.dir");
            UiAutomator2Options options = new UiAutomator2Options()
                .setUdid(deviceId)
                .setPlatformName("Android")
                .setAutomationName("UiAutomator2")
                .setNoReset(true)
                .setAdbExecTimeout(Duration.ofSeconds(30));
            
            // 使用 setCapability 以確保相容性，強制指定 Android SDK 路徑
            options.setCapability("appium:androidHome", new File(projectRoot).getAbsolutePath());
            options.setCapability("appium:androidSdkRoot", new File(projectRoot).getAbsolutePath());

            URL serverUrl = new URL("http://127.0.0.1:4723");
            driver = new AndroidDriver(serverUrl, options);

            // Get device model for folder name
            String deviceModel = (String) driver.getCapabilities().getCapability("deviceModel");
            if (deviceModel == null) {
                // Fallback using shell via Appium if capability is missing
                deviceModel = executeShell(driver, "getprop", "ro.product.model").trim();
            }
            
            String sanitizedModel = deviceModel.replaceAll("[^a-zA-Z0-9]", "_");
            String sanitizedId = deviceId.replaceAll("[^a-zA-Z0-9]", "_");
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String baseDir = "Records" + File.separator + sanitizedModel + "_" + sanitizedId + File.separator + timestamp;
            
            Files.createDirectories(Paths.get(baseDir));

            // 1. Screenshot via Appium
            File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            File destFile = new File(baseDir + File.separator + "screenshot.png");
            FileUtils.copyFile(srcFile, destFile);

            // 2. Processes via Appium Shell
            String processes = executeShell(driver, "ps", "-A");
            Files.write(Paths.get(baseDir + File.separator + "processes.txt"), processes.getBytes(StandardCharsets.UTF_8));

            // 3. Device Info via Appium Shell
            String deviceInfo = executeShell(driver, "getprop", "");
            Files.write(Paths.get(baseDir + File.separator + "device_info.txt"), deviceInfo.getBytes(StandardCharsets.UTF_8));

            System.out.println("Appium: Recorded device state for " + deviceId + " in " + baseDir);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Appium Error: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private static String executeShell(AndroidDriver driver, String command, String args) {
        try {
            // Appium's mobile: shell command
            // Note: Requires Appium server to be started with --allow-insecure=mobile_shell
            return (String) driver.executeScript("mobile: shell", Map.of(
                "command", command,
                "args", args.split("\\s+")
            ));
        } catch (Exception e) {
            return "Shell Error: " + e.getMessage() + "\n(Make sure Appium server has --allow-insecure=mobile_shell)";
        }
    }
}
