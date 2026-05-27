# Android 無線偵錯配對指南 (Wi-Fi ADB Guide)

本指南說明如何透過 Android 11+ 的「無線偵錯」功能，在不連接 USB 線的情況下與電腦進行配對並建立 Appium 控制連線。

## 📱 手機端設定步驟

1.  **進入開發者模式**：
    *   開啟「設定」 -> 「關於手機」 -> 連續點擊「版本號碼」直到顯示已啟動開發人員選項。
2.  **開啟無線偵錯**：
    *   進入「設定」 -> 「系統」 -> 「開發人員選項」。
    *   找到「**無線偵錯**」選項。
    *   **重要：** 不要只打開開關，請直接 **點擊「無線偵錯」文字部分** 進入詳細設定頁面。
3.  **啟動配對模式**：
    *   點擊「**使用配對碼配對裝置**」。
    *   畫面會顯示：
        *   **IP 地址和埠號** (例如：`192.168.1.10:33455`)
        *   **Wi-Fi 配對碼** (6 位數字)

---

## 💻 電腦端操作步驟

1.  **第一步：執行配對指令 (adb pair)**：
    *   使用手機上「**使用配對碼配對裝置**」彈窗中顯示的 IP 與 **配對埠號**。
    ```bash
    adb pair [IP]:[Pairing_Port]
    # 範例：adb pair 192.168.0.144:45573
    ```
    *   輸入手機顯示的 **6 位配對碼**。

2.  **第二步：執行連線指令 (adb connect)**：
    *   **重要：** 配對成功後，請關閉配對彈窗，回到「無線偵錯」主頁面。
    *   使用主頁面上顯示的「IP 地址和埠號」（這通常與剛才配對的埠號 **不同**）。
    ```bash
    adb connect [IP]:[Connect_Port]
    # 範例：adb connect 192.168.0.144:38921
    ```

3.  **第三步：確認狀態**：
    ```bash
    adb devices
    ```
    *   應顯示 `[IP]:[Connect_Port] device`。

---

## 🛠️ 常見問題處理

### ❌ 錯誤 10061 (目標電腦拒絕連線)
這代表你連接的 IP 或埠號沒有服務在監聽。
*   **原因 A：使用了「配對埠號」來連線**。配對成功後，配對分頁的埠號會失效。請回到「無線偵錯」主頁面查看最新的連線埠號。
*   **原因 B：無線偵錯已自動關閉**。若手機螢幕關閉或 Wi-Fi 斷線，無線偵錯可能會自動關閉，請重新開啟。
*   **原因 C：防火牆擋住**。請確認電腦防火牆允許 ADB 通訊。

### ❌ 執行 adb connect 顯示 "already connected" 但 adb devices 沒裝置
請嘗試重置 ADB：
```bash
adb kill-server
adb start-server
```
然後再次執行 `adb connect`。

### 截圖
adb -s 192.168.43.187:43591 exec-out screencap -p > ../captures/screen.png
adb pair 192.168.43.187:39487
adb connect 192.168.0.150:41005
adb disconnect
adb kill-server
adb start-server
adb devices

--切換幣種
adb -s adb-SM02G4061956964-XYxwQZ._adb-tls-connect._tcp shell input tap 600 1160
--50%
adb -s adb-SM02G4061956964-XYxwQZ._adb-tls-connect._tcp shell input tap 770 1060
--25%
adb -s adb-SM02G4061956964-XYxwQZ._adb-tls-connect._tcp shell input tap 900 1060
--Swap
adb -s adb-SM02G4061956964-XYxwQZ._adb-tls-connect._tcp shell input tap 600 1850
--核准
adb -s adb-SM02G4061956964-XYxwQZ._adb-tls-connect._tcp shell input tap 600 2440

adb -s adb-SM02G4061956964-XYxwQZ._adb-tls-connect._tcp exec-out screencap -p > ../captures/screen.png
adb connect 192.168.0.150:
adb connect 192.168.0.213:
adb connect 192.168.0.144:

adb connect 192.168.0.150:41649
adb connect 192.168.0.213:38625
adb connect 192.168.0.144:32991

--列出所有 App
adb -s 192.168.0.144:45633 shell pm list packages
--僅列出第三方 App（排除系統內建）
adb -s 192.168.0.144:45633 shell pm list packages -3

--取得當前應用程式
adb -s 192.168.0.144:45633 shell dumpsys window | findstr "mCurrentFocus"
adb -s 192.168.0.144:37629 shell dumpsys window | findstr "mCurrentFocus"
--如果無法取得當前應用程式，可以用搜尋的，可以取得pkg和cmp
adb -s 192.168.0.144:45633 shell dumpsys activity activities | findstr "lince"
adb -s 192.168.0.144:45633 shell dumpsys activity activities | findstr "degencoinflip"

--開啟應用程式
adb -s 192.168.0.144:45633 shell am start -n app.backpack.mobile.standalone/app.backpack.mobile.standalone.MainActivity
adb -s 192.168.0.144:37629 shell am start -n com.solanamobile.wallet/com.solflare.mobile.MainActivity
adb -s 192.168.0.144:37629 shell am start -n com.solflare.mobile/com.solflare.mobile.MainActivity
--強制停止
adb -s 192.168.0.144:45633 shell am force-stop app.backpack.mobile.standalone
adb -s 192.168.0.144:37629 shell am force-stop com.solflare.mobile

adb -s 192.168.0.144:45633 shell am start -n finance.lince.app/finance.lince.app.LauncherActivity
adb -s 192.168.0.144:45633 shell am start -n com.degencoinflip.app.twa/com.degencoinflip.app.twa.LauncherActivity
adb -s 192.168.0.144:45633 shell am start -n com.bringyour.network/com.bringyour.network.MainActivity
adb -s 192.168.0.144:45633 shell am start -n io.getgrass.www/io.getgrass.www.MainActivity
adb -s 192.168.0.144:45633 shell am start -n com.solanamobile.wallet/com.solflare.mobile.MainActivity

adb -s 192.168.0.144:38273 shell am start -n finance.lince.app/finance.lince.app.LauncherActivity --windowingMode 5
adb -s 192.168.0.144:38273 shell am start -n com.bringyour.network/com.bringyour.network.MainActivity --windowingMode 5
adb -s 192.168.0.144:38273 shell am start -n com.degencoinflip.app.twa/com.degencoinflip.app.twa.LauncherActivity --windowingMode 5
adb -s 192.168.0.144:38273 shell am start -n app.backpack.mobile.standalone/app.backpack.mobile.standalone.MainActivity --windowingMode 5
--「清除資料」並關閉
adb -s 192.168.0.144:45633 shell pm clear app.backpack.mobile.standalone

--固定Port
adb -s 192.168.0.113:42021 tcpip 41411