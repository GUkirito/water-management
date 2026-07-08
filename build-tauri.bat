@echo off
setlocal
cd /d "%~dp0"

if defined TAURI_SIGNING_PRIVATE_KEY goto signing_key_ok
if not exist ".tauri\update-key.pem" goto signing_key_ok
set "TAURI_SIGNING_PRIVATE_KEY=%CD%\.tauri\update-key.pem"
:signing_key_ok

where makensis.exe >nul 2>nul
if not errorlevel 1 goto nsis_ok
if exist "D:\Program Files (x86)\NSIS\makensis.exe" (
  set "PATH=D:\Program Files (x86)\NSIS;%PATH%"
  goto nsis_ok
)
echo Missing NSIS. Install NSIS, then add its install folder to PATH.
echo Common path: D:\Program Files (x86)\NSIS
exit /b 1
:nsis_ok

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
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item 'src-tauri\target\release\bundle\msi\*.msi','src-tauri\target\release\bundle\msi\*.sig','src-tauri\target\release\bundle\nsis\*.exe','src-tauri\target\release\bundle\nsis\*.sig' -Force -ErrorAction SilentlyContinue"
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
cargo.exe tauri build --bundles msi nsis --ci
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo Copying installers to installer...
if not exist "installer" mkdir "installer"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$version=(Get-Content 'src-tauri\tauri.conf.json' -Raw -Encoding UTF8 | ConvertFrom-Json).version; $installer='installer'; Get-ChildItem $installer -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -like '*_x64_zh-CN.msi' -or $_.Name -like '*_x64_zh-CN.msi.sig' -or $_.Name -like '*_x64-setup.exe' -or $_.Name -like '*_x64-setup.exe.sig' } | Remove-Item -Force; $msi=Get-ChildItem 'src-tauri\target\release\bundle\msi' -Filter ('*_'+$version+'_x64_zh-CN.msi') | Select-Object -First 1; $exe=Get-ChildItem 'src-tauri\target\release\bundle\nsis' -Filter ('*_'+$version+'_x64-setup.exe') | Select-Object -First 1; if(-not $msi){ throw ('MSI installer not found for version '+$version) }; if(-not $exe){ throw ('NSIS installer not found for version '+$version) }; Copy-Item $msi.FullName $installer -Force; Copy-Item ($msi.FullName+'.sig') $installer -Force; Copy-Item $exe.FullName $installer -Force; Copy-Item ($exe.FullName+'.sig') $installer -Force"
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo Generating latest.json...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$version=(Get-Content 'src-tauri\tauri.conf.json' -Raw -Encoding UTF8 | ConvertFrom-Json).version; $pattern='*_'+$version+'_x64-setup.exe'; $assetName='_'+$version+'_x64-setup.exe'; $exe=Get-ChildItem 'installer' -Filter '*_x64-setup.exe' | Where-Object { $_.Name -like $pattern } | Sort-Object @{ Expression={ if($_.Name -eq $assetName){0}else{1} } }, LastWriteTime -Descending | Select-Object -First 1; if(-not $exe){ throw ('NSIS installer not found for version '+$version) }; $sigPath=$exe.FullName+'.sig'; if(-not (Test-Path $sigPath)){ throw ('Signature not found: '+$exe.Name+'.sig') }; $assetPath=Join-Path $exe.DirectoryName $assetName; if($exe.Name -ne $assetName){ Copy-Item $exe.FullName $assetPath -Force; Copy-Item $sigPath ($assetPath+'.sig') -Force; $exe=Get-Item $assetPath; $sigPath=$exe.FullName+'.sig' }; $sig=(Get-Content $sigPath -Raw -Encoding UTF8).Trim(); $url='https://github.com/GUkirito/water-management/releases/latest/download/'+[uri]::EscapeDataString($exe.Name); $json=[ordered]@{ version=$version; notes='fixed known bugs'; pub_date=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); platforms=[ordered]@{ 'windows-x86_64'=[ordered]@{ signature=$sig; url=$url } } } | ConvertTo-Json -Depth 5; [System.IO.File]::WriteAllText((Resolve-Path 'installer\latest.json'), $json, (New-Object System.Text.UTF8Encoding($false))); Write-Host ('Generated installer\latest.json -> '+$exe.Name)"
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo Done. Installers are in:
echo   installer\
endlocal
