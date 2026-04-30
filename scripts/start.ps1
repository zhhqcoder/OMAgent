# OMAgent 启动脚本
# 用法: .\scripts\start.ps1 [dev] （从项目根目录执行）
#   默认启动本地开发模式
#   dev: 使用环境变量配置（application-dev.yml）
# 功能: 使用外部config目录的配置文件和提示词启动应用

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir
$javaHome = "C:\Program Files\Java\jdk-17"
$jarPath = "$projectDir\target\omagent-1.0.0.jar"
$configDir = "$projectDir\config"
$logDir = "$projectDir\logs"
$logFile = "$logDir\start.log"

# 确保日志目录存在
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

# 检查JAR文件
if (-not (Test-Path $jarPath)) {
    Write-Host "JAR not found: $jarPath" -ForegroundColor Red
    Write-Host "Run .\scripts\build.ps1 first" -ForegroundColor Yellow
    exit 1
}

# 检查config目录
if (-not (Test-Path "$configDir\application.yml")) {
    Write-Host "Config not found: $configDir\application.yml" -ForegroundColor Red
    Write-Host "Run .\scripts\build.ps1 first" -ForegroundColor Yellow
    exit 1
}

# 停止已有进程
$existing = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Stopping existing Java process..." -ForegroundColor Yellow
    $existing | Stop-Process -Force
    Start-Sleep -Seconds 2
}

# 构建启动参数
$profile = if ($args[0] -eq "dev") { "dev" } else { "" }

# -Dfile.encoding=UTF-8: 确保Java输出UTF-8编码，避免日志乱码
# --spring.config.location: 指定外部配置文件（覆盖jar内置的）
# --spring.config.additional-location: 追加额外配置（如dev profile环境变量版本）
$javaArgs = @(
    "-Dfile.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
    "-jar", $jarPath,
    "--spring.config.location=file:$configDir/",
    "--spring.config.additional-location=optional:classpath:/"
)

if ($profile -eq "dev") {
    $javaArgs += "--spring.profiles.active=dev"
    Write-Host "=== OMAgent Start (dev profile) ===" -ForegroundColor Cyan
} else {
    Write-Host "=== OMAgent Start ===" -ForegroundColor Cyan
}

Write-Host "  JAR: $jarPath"
Write-Host "  Config: $configDir/"
Write-Host "  Prompts: $configDir\prompts\"
Write-Host "  Log: $logFile"

# 使用 chcp 65001 切换控制台为 UTF-8 代码页，再启动 Java 进程
# 通过 cmd /c 重定向输出，确保日志文件以 UTF-8 编码写入
$argString = $javaArgs -join " "
$cmdLine = "chcp 65001 >nul & `"$javaHome\bin\java.exe`" $argString"

$process = Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", $cmdLine `
    -WorkingDirectory $projectDir `
    -PassThru `
    -NoNewWindow:$false

Write-Host "  PID: $($process.Id)" -ForegroundColor Green
Write-Host "Waiting for startup..." -ForegroundColor Yellow

# 等待启动
$maxWait = 40
$waited = 0
while ($waited -lt $maxWait) {
    Start-Sleep -Seconds 2
    $waited += 2
    if (Test-Path $logFile) {
        $log = Get-Content $logFile -Encoding UTF8 -ErrorAction SilentlyContinue
        if ($log -match "Started OmAgentApplication") {
            Write-Host "Application started successfully! (took ${waited}s)" -ForegroundColor Green
            Write-Host "  URL: http://localhost:8080"
            exit 0
        }
        if ($log -match "APPLICATION FAILED TO START") {
            Write-Host "Application failed to start!" -ForegroundColor Red
            $log | Select-String "ERROR|Failed|Caused by" | Select-Object -Last 5 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
            exit 1
        }
    }
}

Write-Host "Startup check timed out (${maxWait}s). Check logs\start.log manually." -ForegroundColor Yellow
