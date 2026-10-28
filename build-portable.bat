@echo off
setlocal
echo ========================================================
echo   Building Scribidoo Standalone Portable for Windows...
echo ========================================================

cd /d "%~dp0"

call .\gradlew.bat --no-daemon --console=plain :desktop:packagePortableZip

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo   SUCCESS! Portable build ready in dist\ folder:
    echo     - Unpacked Folder: dist\Scribidoo-Portable\Scribidoo.exe
    echo     - Zip Archive:     dist\Scribidoo-Portable.zip
    echo.
    echo   Just copy dist\Scribidoo-Portable.zip to your laptop,
    echo   unzip, and launch Scribidoo.exe. No install needed!
    echo ========================================================
    if exist dist explorer dist
) else (
    echo.
    echo [ERROR] Build failed with exit code %ERRORLEVEL%.
)

endlocal
pause
