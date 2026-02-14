@echo off
chcp 65001 >nul
setlocal

:: 設定專案根目錄路徑 (使用當前腳本所在的目錄)
set "PROJECT_ROOT=%~dp0"
if "%PROJECT_ROOT:~-1%"=="\" set "PROJECT_ROOT=%PROJECT_ROOT:~0,-1%"

:: 設定 Android SDK 環境變數
set "ANDROID_HOME=%PROJECT_ROOT%"
set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"

echo ==========================================
echo   Appium Server 快速啟動腳本
echo ==========================================
echo ANDROID_HOME 指向: %ANDROID_HOME%
echo.
echo 正在檢查 ADB 狀態...
adb devices
echo.
echo 正在啟動 Appium Server...
echo (按下 Ctrl+C 可停止伺服器)
echo ------------------------------------------

:: 啟動 Appium
appium

pause
