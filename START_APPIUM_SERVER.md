# Appium Server 啟動指南

為了確保 Appium Server 能夠正確識別 Android SDK 並驅動設備，請依照以下步驟啟動 Server。

## 🚀 啟動步驟 (Windows PowerShell)

請開啟 **PowerShell** 終端機，並依序輸入以下指令：

1. **設定 Android SDK 環境變數** (僅針對當前視窗有效)：
   ```powershell
   $env:ANDROID_HOME = "C:\Users\AnkaLu\Desktop\Home\GitHub_WorkSpace\Seeker_Plugin"
   ```

2. **確認變數是否設定成功** (可選)：
   ```powershell
   echo $env:ANDROID_HOME
   ```

3. **啟動 Appium Server**：
   ```powershell
   appium
   ```

---

## 📝 注意事項
- **不要關閉視窗**：Appium Server 必須持續執行，Java 程式才能連線成功。
- **驅動程式**：請確保已安裝 `uiautomator2` 驅動（若未安裝，請執行 `appium driver install uiautomator2`）。
- **SDK 路徑**：本專案預設將 SDK 工具放在專案目錄下的 `platform-tools` 中，因此 `ANDROID_HOME` 指向專案根目錄。
