@echo off
chcp 65001 >nul
title 村级自来水管理系统 - 打包工具

REM 将 WiX 工具添加到 PATH（jpackage 需要）
set "PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin"

echo.
echo  ╔══════════════════════════════════════════════╗
echo  ║   村级自来水管理系统 - Windows 安装包构建    ║
echo  ╚══════════════════════════════════════════════╝
echo.

REM ================================================================
REM  第 1 步：构建前端
REM ================================================================
echo  [1/5] 正在构建前端...
cd /d "%~dp0frontend"
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo  ❌ 前端构建失败！
    pause
    exit /b 1
)
echo  ✅ 前端构建完成

REM ================================================================
REM  第 2 步：构建后端 JAR
REM ================================================================
echo.
echo  [2/5] 正在构建后端 JAR...
cd /d "%~dp0"
call mvnw.cmd clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo  ❌ 后端构建失败！
    pause
    exit /b 1
)
echo  ✅ 后端构建完成

REM ================================================================
REM  第 3 步：分析 Java 模块依赖（jdeps）
REM ================================================================
echo.
echo  [3/5] 正在分析 Java 模块依赖...

REM 找到生成的 JAR 文件
for /f "delims=" %%i in ('dir /b /s "%~dp0target\*-SNAPSHOT.jar" 2^>nul') do set JAR_FILE=%%i

if "%JAR_FILE%"=="" (
    echo  ❌ 未找到 JAR 文件！请检查 target 目录
    pause
    exit /b 1
)
echo  📦 JAR 文件: %JAR_FILE%

REM 使用 jdeps 分析模块依赖
jdeps --multi-release 25 --print-module-deps --ignore-missing-deps "%JAR_FILE%" > "%~dp0target\module-deps.txt" 2>nul

if %ERRORLEVEL% NEQ 0 (
    echo  ⚠  jdeps 分析遇到警告，使用默认模块列表
    echo java.base,java.sql,java.naming,java.management,java.instrument,java.security.jgss,java.desktop,jdk.unsupported > "%~dp0target\module-deps.txt"
)

REM 补充 Spring Boot 需要的模块
set MODULES=java.base,java.sql,java.naming,java.management,java.instrument,java.security.jgss,java.desktop,jdk.unsupported,java.xml,java.logging,java.net.http

echo  需要的模块: %MODULES%
echo  ✅ 模块分析完成

REM ================================================================
REM  第 4 步：用 jlink 裁剪 JRE
REM ================================================================
echo.
echo  [4/5] 正在裁剪 JRE（生成精简版 Java 运行时）...

set CUSTOM_JRE="%~dp0target\custom-jre"

REM 删除旧的 JRE（如果存在）
if exist %CUSTOM_JRE% rmdir /s /q %CUSTOM_JRE%

jlink ^
    --module-path "%JAVA_HOME%\jmods" ^
    --add-modules %MODULES% ^
    --output %CUSTOM_JRE% ^
    --strip-debug ^
    --compress zip-6 ^
    --no-header-files ^
    --no-man-pages

if %ERRORLEVEL% NEQ 0 (
    echo  ❌ JRE 裁剪失败！
    pause
    exit /b 1
)
echo  ✅ JRE 裁剪完成

REM ================================================================
REM  第 5 步：用 jpackage 生成 Windows 安装包
REM ================================================================
echo.
echo  [5/5] 正在生成 Windows 安装程序...

set OUTPUT_NAME=村级自来水管理系统
set APP_VERSION=1.0.0

jpackage ^
    --type exe ^
    --name "%OUTPUT_NAME%" ^
    --app-version %APP_VERSION% ^
    --input "%~dp0target" ^
    --main-jar "%~nx1%JAR_FILE%" ^
    --main-class org.springframework.boot.loader.JarLauncher ^
    --runtime-image %CUSTOM_JRE% ^
    --win-console ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut ^
    --vendor "村委会" ^
    --description "村级自来水管理系统" ^
    --dest "%~dp0installer" ^
    --verbose

if %ERRORLEVEL% NEQ 0 (
    echo  ❌ 安装包生成失败！
    pause
    exit /b 1
)

echo.
echo  ╔══════════════════════════════════════════════╗
echo  ║  🎉 安装包生成成功！                        ║
echo  ╠══════════════════════════════════════════════╣
echo  ║  文件位置: installer\村级自来水管理系统.exe  ║
echo  ╚══════════════════════════════════════════════╝
echo.

pause
