@echo off
title Rudy Browser - Pixel Phone Screen
echo ===================================================
echo Membuka Jendela Layar HP (Pixel API 35)...
echo ===================================================
start "" "%LOCALAPPDATA%\scrcpy\scrcpy.exe" -s emulator-5554 --window-title "Rudy Browser - Pixel Phone" --always-on-top
