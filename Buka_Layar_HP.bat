@echo off
setlocal enabledelayedexpansion
title "Rudy Browser - Layar HP & Emulator Launcher"

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "EMULATOR=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"
set "SCRCPY=%LOCALAPPDATA%\scrcpy\scrcpy.exe"
set "AVD_NAME=Pixel_API_35"
set "APP_PKG=io.rudycity.tvbrowser.previewcompose071"

echo ========================================================
echo       Rudy Browser - Layar HP ^& Emulator Manager
echo ========================================================
echo.

:: 1. Pastikan ADB Server Aktif
echo [1/4] Memeriksa status ADB server...
if not exist "%ADB%" (
    echo [ERROR] ADB tidak ditemukan di: %ADB%
    pause
    exit /b 1
)
"%ADB%" start-server >nul 2>&1

:: 2. Periksa apakah Emulator / Device sudah terhubung
set "DEVICE_FOUND=0"
set "DEVICE_ID="
for /f "tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        set "DEVICE_FOUND=1"
        set "DEVICE_ID=%%A"
    )
)

if "!DEVICE_FOUND!"=="1" (
    echo [OK] Device/Emulator terdeteksi aktif: !DEVICE_ID!
    goto launch_app
)

echo [2/4] Emulator belum aktif. Menyalakan !AVD_NAME!...
if not exist "%EMULATOR%" (
    echo [ERROR] Emulator tidak ditemukan di: %EMULATOR%
    pause
    exit /b 1
)

start "" "%EMULATOR%" -avd !AVD_NAME!
echo Menunggu emulator terhubung ke ADB...
"%ADB%" wait-for-device

echo Menunggu emulator selesai boot (mohon tunggu beberapa saat)...
:wait_boot
ping -n 3 127.0.0.1 >nul
for /f %%C in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do (
    if "%%C"=="1" goto boot_done
)
goto wait_boot

:boot_done
echo [OK] Android Emulator berhasil boot!
"%ADB%" shell input keyevent 82 >nul 2>&1

:launch_app
:: 3. Luncurkan Aplikasi Rudy Browser
echo [3/4] Memastikan aplikasi Rudy Browser terbuka...
"%ADB%" shell monkey -p %APP_PKG% -c android.intent.category.LAUNCHER 1 >nul 2>&1
ping -n 2 127.0.0.1 >nul

:: 4. Buka Layar Interaktif scrcpy
echo [4/4] Membuka jendela interaktif Layar HP (scrcpy)...
if not exist "%SCRCPY%" (
    echo [ERROR] scrcpy tidak ditemukan di: %SCRCPY%
    pause
    exit /b 1
)

start "" "%SCRCPY%" --window-title "Rudy Browser - Pixel Phone" --always-on-top

echo.
echo ========================================================
echo Jendela Layar HP (scrcpy) sudah aktif dan siap digunakan!
echo ========================================================
ping -n 3 127.0.0.1 >nul
exit
