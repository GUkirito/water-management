@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Building frontend...
cd frontend
call npm.cmd run build
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
cd ..

echo [2/4] Building backend...
call mvnw.cmd package -DskipTests
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo [3/4] Creating custom JRE...
if not exist "target\custom-jre\bin\java.exe" (
  jlink --module-path "%JAVA_HOME%\jmods" --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.sql.rowset,java.xml.crypto,jdk.jfr,jdk.unsupported,java.sql,java.naming,java.xml --output "target\custom-jre" --strip-debug --compress zip-6 --no-header-files --no-man-pages
  if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)

echo [4/4] Building Tauri desktop app...
cargo.exe tauri --version >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
  echo Missing Tauri CLI. Run: cargo install tauri-cli --version "^2"
  exit /b 1
)
cargo.exe tauri build
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo Copying installers to installer...
if not exist "installer" mkdir "installer"
copy /Y "src-tauri\target\release\bundle\msi\*.msi" "installer\" >nul
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
copy /Y "src-tauri\target\release\bundle\nsis\*.exe" "installer\" >nul
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo Done. Installers are in:
echo   installer\
endlocal
