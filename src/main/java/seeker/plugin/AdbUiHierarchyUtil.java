package seeker.plugin;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdbUiHierarchyUtil {
    private static final Pattern BOUNDS_PATTERN = Pattern.compile("\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]");
    private static final String SOLFLARE_PACKAGE = "com.solflare.mobile";
    private static final String SOLFLARE_MAIN_ACTIVITY = "com.solflare.mobile/com.solflare.mobile.MainActivity";

    private AdbUiHierarchyUtil() {
    }

    public static String detectAdbPath() {
        String projectRoot = System.getProperty("user.dir");
        File adbFile = new File(projectRoot + File.separator + "platform-tools" + File.separator + "adb.exe");
        return adbFile.exists() ? adbFile.getAbsolutePath() : "adb";
    }

    public static List<String> getAdbDevices() {
        List<String> devices = new ArrayList<>();
        try {
            ProcessResult result = runCommand(List.of(detectAdbPath(), "devices"));
            if (result.exitCode != 0) {
                return devices;
            }

            boolean foundHeader = false;
            for (String rawLine : result.output.split("\\R")) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("List of devices attached")) {
                    foundHeader = true;
                    continue;
                }
                if (foundHeader && line.matches("\\S+\\s+device")) {
                    devices.add(line.split("\\s+")[0]);
                }
            }
        } catch (Exception e) {
            System.err.println("ADB 裝置清單讀取失敗: " + e.getMessage());
        }
        return devices;
    }

    public static String getCurrentScreenXml(String deviceId) throws Exception {
        ProcessResult result = dumpUiHierarchy(deviceId, false);
        if (result.exitCode != 0 && isIdleStateError(result.output)) {
            Thread.sleep(800);
            result = dumpUiHierarchy(deviceId, true);
        }
        if (result.exitCode != 0) {
            throw new IllegalStateException(buildDumpErrorMessage(result.output));
        }
        return extractXml(result.output);
    }

    public static void restartSolflare(String deviceId) throws Exception {
        restartApp(deviceId, SOLFLARE_PACKAGE, SOLFLARE_MAIN_ACTIVITY);
    }

    public static void restartApp(String deviceId, String packageName, String componentName) throws Exception {
        runAdbShellCommand(deviceId, "am", "force-stop", packageName);
        Thread.sleep(500);
        runAdbShellCommand(deviceId, "am", "start", "-n", componentName);
    }

    public static String runAdbShellCommand(String deviceId, String... shellArgs) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                detectAdbPath(),
                "-s",
                deviceId,
                "shell"
        ));
        command.addAll(List.of(shellArgs));
        ProcessResult result = runCommand(command);
        if (result.exitCode != 0) {
            throw new IllegalStateException("ADB 指令失敗: " + result.output.trim());
        }
        return result.output;
    }

    public static void tapRandomPointInBounds(String deviceId, String bounds) throws Exception {
        Bounds parsedBounds = parseBounds(bounds);
        int x = ThreadLocalRandom.current().nextInt(parsedBounds.left(), parsedBounds.right() + 1);
        int y = ThreadLocalRandom.current().nextInt(parsedBounds.top(), parsedBounds.bottom() + 1);
        runAdbShellCommand(deviceId, "input", "tap", String.valueOf(x), String.valueOf(y));
    }

    private static ProcessResult dumpUiHierarchy(String deviceId, boolean compressed) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                detectAdbPath(),
                "-s",
                deviceId,
                "exec-out",
                "uiautomator",
                "dump"
        ));
        if (compressed) {
            command.add("--compressed");
        }
        command.add("/dev/tty");
        return runCommand(command);
    }

    private static boolean isIdleStateError(String output) {
        return output != null && output.toLowerCase().contains("could not get idle state");
    }

    private static String buildDumpErrorMessage(String output) {
        String message = output == null ? "" : output.trim();
        if (isIdleStateError(message)) {
            return "uiautomator dump 失敗：畫面持續變動，Android 無法取得 idle state。"
                    + "請先停留在較穩定的畫面，或關閉動畫/等待載入完成後重試。原始錯誤: " + message;
        }
        return "uiautomator dump 失敗: " + message;
    }

    public static List<TextNodeBounds> parseTextBounds(String xml) throws Exception {
        List<TextNodeBounds> items = new ArrayList<>();
        Document document = parseDocument(xml);
        NodeList nodes = document.getElementsByTagName("node");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String text = element.getAttribute("text");
            if (text == null || text.isBlank()) {
                text = element.getAttribute("content-desc");
            }
            String bounds = element.getAttribute("bounds");
            Matcher matcher = BOUNDS_PATTERN.matcher(bounds);
            if (text != null && !text.isBlank() && matcher.matches()) {
                Bounds parsedBounds = toBounds(matcher, bounds);
                items.add(new TextNodeBounds(text, element.getAttribute("resource-id"), bounds,
                        parsedBounds.left(), parsedBounds.top(), parsedBounds.right(), parsedBounds.bottom()));
            }
        }
        return items;
    }

    public static List<ResourceNode> parseResourceNodes(String xml) throws Exception {
        List<ResourceNode> items = new ArrayList<>();
        Document document = parseDocument(xml);
        NodeList nodes = document.getElementsByTagName("node");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String resourceId = element.getAttribute("resource-id");
            String bounds = element.getAttribute("bounds");
            Matcher matcher = BOUNDS_PATTERN.matcher(bounds);
            if (resourceId != null && !resourceId.isBlank() && matcher.matches()) {
                Bounds parsedBounds = toBounds(matcher, bounds);
                items.add(new ResourceNode(
                        resourceId,
                        element.getAttribute("text"),
                        element.getAttribute("content-desc"),
                        element.getAttribute("class"),
                        bounds,
                        parsedBounds.left(),
                        parsedBounds.top(),
                        parsedBounds.right(),
                        parsedBounds.bottom()
                ));
            }
        }
        return items;
    }

    public static List<StageNode> detectStageNodes(String xml) throws Exception {
        List<StageNode> items = new ArrayList<>();
        Document document = parseDocument(xml);
        NodeList nodes = document.getElementsByTagName("node");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String bounds = element.getAttribute("bounds");
            Matcher matcher = BOUNDS_PATTERN.matcher(bounds);
            if (!matcher.matches()) {
                continue;
            }

            String stage = detectStage(element);
            if (stage == null) {
                continue;
            }

            Bounds parsedBounds = toBounds(matcher, bounds);
            items.add(new StageNode(
                    stage,
                    element.getAttribute("resource-id"),
                    element.getAttribute("text"),
                    element.getAttribute("content-desc"),
                    element.getAttribute("class"),
                    bounds,
                    parsedBounds.left(),
                    parsedBounds.top(),
                    parsedBounds.right(),
                    parsedBounds.bottom()
            ));
        }
        return items;
    }

    public static Bounds parseBounds(String bounds) {
        Matcher matcher = BOUNDS_PATTERN.matcher(bounds == null ? "" : bounds);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("bounds 格式錯誤: " + bounds);
        }
        return toBounds(matcher, bounds);
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static Bounds toBounds(Matcher matcher, String bounds) {
        return new Bounds(
                bounds,
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                Integer.parseInt(matcher.group(4))
        );
    }

    private static String detectStage(Element element) {
        String resourceId = element.getAttribute("resource-id");
        String text = element.getAttribute("text");
        String contentDesc = element.getAttribute("content-desc");
        String className = element.getAttribute("class");

        if ("btn-portfolio-hero-card-copy-address".equals(resourceId)) {
            return "Solflare首頁";
        }
        if ("btn-browser-home".equals(resourceId)) {
            return "Solflare探索";
        }
        if ("探索".equals(contentDesc)) {
            return "Solflare首頁";
        }
        if ("%".equals(text)) {
            return "Titan-%";
        }
        if ("50%".equals(text)) {
            return "Titan-50%";
        }
        if ("25%".equals(text)) {
            return "Titan-25%";
        }
        if ("Swap".equals(text) && resourceId.isBlank() && "android.widget.Button".equals(className)) {
            return "Titan-Swap";
        }
        if ("btn-confirm-transaction-approve-with-seed-vault".equals(resourceId)) {
            return "Titan-批准";
        }
        if ("核准".equals(text)) {
            return "Seed-核准";
        }
        if ("輸入密碼".equals(text)) {
            return "Seed-輸入密碼";
        }
        if ("0".equals(text)) {
            return "Seed-輸入密碼0";
        }
        return null;
    }

    private static String extractXml(String output) {
        int start = output.indexOf("<?xml");
        if (start < 0) {
            start = output.indexOf("<hierarchy");
        }
        if (start < 0) {
            return output.trim();
        }
        int end = output.indexOf("</hierarchy>", start);
        if (end >= 0) {
            return output.substring(start, end + "</hierarchy>".length()).trim();
        }
        return output.substring(start).trim();
    }

    private static ProcessResult runCommand(List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        byte[] outputBytes = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, new String(outputBytes, StandardCharsets.UTF_8));
    }

    private record ProcessResult(int exitCode, String output) {
    }

    public record TextNodeBounds(String text, String resourceId, String bounds, int left, int top, int right, int bottom) {
        public int centerX() {
            return (left + right) / 2;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }
    }

    public record ResourceNode(String resourceId, String text, String contentDesc, String className, String bounds, int left, int top, int right, int bottom) {
        public int centerX() {
            return (left + right) / 2;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }
    }

    public record StageNode(String stage, String resourceId, String text, String contentDesc, String className, String bounds, int left, int top, int right, int bottom) {
        public int centerX() {
            return (left + right) / 2;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }
    }

    public record Bounds(String raw, int left, int top, int right, int bottom) {
        public int centerX() {
            return (left + right) / 2;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }
    }
}
