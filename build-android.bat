@echo off
setlocal
echo ========================================================
echo   Building Scribidoo Android APK...
echo ========================================================

cd /d "%~dp0"

call .\gradlew.bat --no-daemon --console=plain :app:assembleDebug

if %ERRORLEVEL% EQU 0 (
    if not exist dist mkdir dist
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "dist\Scribidoo.apk" >nul
    echo.
    echo ========================================================
    echo   SUCCESS! Android APK ready in dist\ folder:
    echo     - dist\Scribidoo.apk
    echo.
    echo   Copy dist\Scribidoo.apk to your Android tablet/phone
    echo   or install directly via ADB:
    echo     adb install -r dist\Scribidoo.apk
    echo ========================================================
    if exist dist explorer dist
) else (
    echo.
    echo [ERROR] Android build failed with exit code %ERRORLEVEL%.
)

endlocal
pause
