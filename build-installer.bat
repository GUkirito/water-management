@echo off
setlocal enabledelayedexpansion
title Water Management - Build Installer

REM ================================================================
REM  Set encoding for Chinese display
REM ================================================================
chcp 65001 >nul 2>nul || chcp 936 >nul 2>nul

REM ================================================================
REM  Add WiX to PATH (needed by jpackage for EXE installer)
REM ================================================================
set "WIX_PATH=C:\Program Files (x86)\WiX Toolset v3.14\bin"
if exist "%WIX_PATH%" set "PATH=%PATH%;%WIX_PATH%"

echo.
echo ================================================
echo   Village Water Management System
echo   Windows Installer Builder
echo ================================================
echo.

REM ================================================================
REM  Step 1: Build frontend
REM ================================================================
echo [1/5] Building frontend...
cd /d "%~dp0frontend"
call npm run build
set "ERR=%ERRORLEVEL%"
cd /d "%~dp0"
if %ERR% NEQ 0 (
    echo [ERROR] Frontend build failed! (code: %ERR%)
    exit /b 1
)
echo [OK] Frontend built

REM ================================================================
REM  Step 2: Build backend JAR
REM ================================================================
echo.
echo [2/5] Building backend JAR...
call "%~dp0mvnw.cmd" clean package -DskipTests -q -f "%~dp0pom.xml"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Backend build failed!
    exit /b 1
)
echo [OK] Backend JAR built

REM ================================================================
REM  Step 3: Locate JAR and set modules
REM ================================================================
echo.
echo [3/5] Locating JAR and setting modules...

set "JAR_FILE="
for %%f in ("%~dp0target\*.jar") do set "JAR_FILE=%%f"
if "%JAR_FILE%"=="" (
    echo [ERROR] No JAR found in target/ directory
    echo   Run "mvnw.cmd package" manually first.
    exit /b 1
)
echo   JAR: %JAR_FILE%

REM Fixed module list verified for Spring Boot + SQLite + Tomcat
set "MODULES=java.base,java.compiler,java.desktop,java.instrument,java.management,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.sql.rowset,java.xml.crypto,jdk.jfr,jdk.unsupported,java.sql,java.naming,java.xml"
echo   Modules: %MODULES%
echo [OK] Ready

REM ================================================================
REM  Step 4: jlink - Create custom JRE
REM ================================================================
echo.
echo [4/5] Creating custom JRE (~50MB)...

set "CUSTOM_JRE=%~dp0target\custom-jre"
if exist "%CUSTOM_JRE%" rmdir /s /q "%CUSTOM_JRE%"

jlink ^
    --module-path "%JAVA_HOME%\jmods" ^
    --add-modules %MODULES% ^
    --output "%CUSTOM_JRE%" ^
    --strip-debug ^
    --compress zip-6 ^
    --no-header-files ^
    --no-man-pages

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] jlink failed!
    echo   Check that JAVA_HOME is set correctly.
    echo   Current JAVA_HOME: %JAVA_HOME%
    exit /b 1
)
echo [OK] Custom JRE created

REM ================================================================
REM  Step 5: jpackage - Generate Windows installer
REM ================================================================
echo.
echo [5/5] Generating Windows installer...

set "OUTPUT_NAME=VillageWaterManagement"
set "APP_VERSION=1.0.0"
set "DEST_DIR=%~dp0installer"

if exist "%DEST_DIR%" rmdir /s /q "%DEST_DIR%"

for %%f in ("%JAR_FILE%") do set "JAR_NAME=%%~nxf"

echo   Trying EXE installer first...
jpackage ^
    --type exe ^
    --name "%OUTPUT_NAME%" ^
    --app-version %APP_VERSION% ^
    --input "%~dp0target" ^
    --main-jar "%JAR_NAME%" ^
    --main-class org.springframework.boot.loader.JarLauncher ^
    --runtime-image "%CUSTOM_JRE%" ^
    --win-console ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut ^
    --vendor "Village Committee" ^
    --description "Water Management System" ^
    --dest "%DEST_DIR%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================
    echo   BUILD SUCCESS!
    echo   %DEST_DIR%\%OUTPUT_NAME%-%APP_VERSION%.exe
    echo ================================================
    goto :done
)

echo   EXE failed - trying portable app-image...
jpackage ^
    --type app-image ^
    --name "%OUTPUT_NAME%" ^
    --app-version %APP_VERSION% ^
    --input "%~dp0target" ^
    --main-jar "%JAR_NAME%" ^
    --main-class org.springframework.boot.loader.JarLauncher ^
    --runtime-image "%CUSTOM_JRE%" ^
    --dest "%DEST_DIR%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ================================================
    echo   BUILD SUCCESS (portable version)!
    echo   %DEST_DIR%\%OUTPUT_NAME%\
    echo   Run %OUTPUT_NAME%.exe inside to start
    echo ================================================
    goto :done
)

echo.
echo [ERROR] jpackage failed completely.
echo   Try running: jpackage --help
exit /b 1

:done
echo.
echo Press any key to exit...
timeout /t 3 >nul
exit /b 0
