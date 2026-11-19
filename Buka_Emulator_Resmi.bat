@echo off
setlocal enabledelayedexpansion
title "Rudy Browser - Android Emulator Resmi"

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "EMULATOR=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"
set "AVD_NAME=Pixel_API_35"
set "APP_PKG=io.rudycity.tvbrowser.previewcompose071"

echo ========================================================
echo       Rudy Browser - Official Android Emulator
echo ========================================================
echo.

"%ADB%" start-server >nul 2>&1

set "DEVICE_FOUND=0"
for /f "tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" set "DEVICE_FOUND=1"
)

if "!DEVICE_FOUND!"=="0" (
    echo Menyalakan Google Android Emulator (!AVD_NAME!)...
    start "" "%EMULATOR%" -avd !AVD_NAME!
    echo Menunggu emulator selesai boot...
    "%ADB%" wait-for-device
    :wait_boot
    ping -n 3 127.0.0.1 >nul
    for /f %%C in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do (
        if "%%C"=="1" goto boot_done
    )
    goto wait_boot
)

:boot_done
echo Membuka aplikasi Rudy Browser...
"%ADB%" shell monkey -p %APP_PKG% -c android.intent.category.LAUNCHER 1 >nul 2>&1
echo Selesai!
ping -n 2 127.0.0.1 >nul
exit
