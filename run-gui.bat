@echo off
setlocal
chcp 65001 >nul

REM ============================================================
REM  Auto_Vtb launcher
REM  GitHub / services.gradle.org are unreachable on this network,
REM  so this script uses a pre-downloaded Gradle + JDK 21 directly.
REM ============================================================

REM --- Locate JDK 21 (required by Gradle 9.6.1) ---
if not defined JAVA_HOME (
    if exist "C:\Users\rr148\.jdks\jdk-21.0.12.1+1\bin\java.exe" (
        set "JAVA_HOME=C:\Users\rr148\.jdks\jdk-21.0.12.1+1"
    )
)

REM --- Use pre-downloaded Gradle 9.6.1; fall back to the wrapper if moved ---
set "GRADLE_BIN=C:\Users\rr148\.gradle-dist\gradle-9.6.1\bin\gradle.bat"
if exist "%GRADLE_BIN%" (
    call "%GRADLE_BIN%" -p "%~dp0." run %*
) else (
    call "%~dp0gradlew.bat" -p "%~dp0." run %*
)
endlocal
