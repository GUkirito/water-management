@echo off
echo.
echo ============================================
echo   Building Water Management System
echo ============================================
echo.

REM ---- Ensure WiX is available (needed for EXE) ----
set "WIX=C:\Program Files (x86)\WiX Toolset v3.14\bin"
if exist "%WIX%\candle.exe" set "PATH=%WIX%;%PATH%"

REM ---- Step 1: Frontend ----
echo [1/4] Building frontend...
cd /d "%~dp0frontend"
call npm run build
if %ERRORLEVEL% NEQ 0 goto :fail
cd /d "%~dp0"
echo       Done.

REM ---- Step 2: Backend ----
echo [2/4] Building backend...
call "%~dp0mvnw.cmd" package -DskipTests -q -f "%~dp0pom.xml"
if %ERRORLEVEL% NEQ 0 goto :fail
echo       Done.

REM ---- Step 3: jlink ----
echo [3/4] Creating custom JRE...
if exist "%~dp0target\custom-jre" rmdir /s /q "%~dp0target\custom-jre"
jlink --module-path "%JAVA_HOME%\jmods" --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.sql.rowset,java.xml.crypto,jdk.jfr,jdk.unsupported,java.sql,java.naming,java.xml --output "%~dp0target\custom-jre" --strip-debug --compress zip-6 --no-header-files --no-man-pages
if %ERRORLEVEL% NEQ 0 goto :fail
echo       Done.

REM ---- Step 4: jpackage ----
echo [4/4] Generating Windows installer...

if exist "%~dp0installer" rmdir /s /q "%~dp0installer"

for %%f in ("%~dp0target\*.jar") do set "JAR_NAME=%%~nxf"

echo   Trying single EXE installer...
jpackage --type exe --name "VillageWaterManagement" --app-version 1.0.0 --input "%~dp0target" --main-jar "%JAR_NAME%" --main-class org.springframework.boot.loader.JarLauncher --runtime-image "%~dp0target\custom-jre" --win-console --win-shortcut --win-menu --win-dir-chooser --vendor "Village Committee" --description "Water Management System" --dest "%~dp0installer"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================
    echo   BUILD SUCCESS - Single EXE installer!
    echo.
    for %%g in ("%~dp0installer\*.exe") do echo   %%g
    echo.
    echo   Just double-click it to install.
    echo ============================================
    goto :end
)

echo   EXE failed, trying portable version...
jpackage --type app-image --name "VillageWaterManagement" --app-version 1.0.0 --input "%~dp0target" --main-jar "%JAR_NAME%" --main-class org.springframework.boot.loader.JarLauncher --runtime-image "%~dp0target\custom-jre" --dest "%~dp0installer"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================
    echo   BUILD SUCCESS - Portable version!
    echo.
    echo     installer\VillageWaterManagement\
    echo     Double-click VillageWaterManagement.exe
    echo ============================================
    goto :end
)

goto :fail

:fail
echo.
echo ============================================
echo   BUILD FAILED!
echo   Check the error messages above.
echo ============================================
:end
pause
