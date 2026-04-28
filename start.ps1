# OMAgent 启动脚本
# 用法: .\start.ps1 [dev]
#   默认启动本地开发模式
#   dev: 使用环境变量配置（application-dev.yml）
# 功能: 使用外部config目录的配置文件和提示词启动应用

$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$javaHome = "C:\Program Files\Java\jdk-17"
$jarPath = "$projectDir\target\omagent-1.0.0.jar"
$configDir = "$projectDir\config"

# 检查JAR文件
if (-not (Test-Path $jarPath)) {
    Write-Host "JAR not found: $jarPath" -ForegroundColor Red
    Write-Host "Run .\build.ps1 first" -ForegroundColor Yellow
    exit 1
}

# 检查config目录
if (-not (Test-Path "$configDir\application.yml")) {
    Write-Host "Config not found: $configDir\application.yml" -ForegroundColor Red
    Write-Host "Run .\build.ps1 first" -ForegroundColor Yellow
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
# --spring.config.location: 指定外部配置文件（覆盖jar内置的）
# --spring.config.additional-location: 追加额外配置（如dev profile环境变量版本）
$profile = if ($args[0] -eq "dev") { "dev" } else { "" }

$javaArgs = @(
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

# 启动应用
$process = Start-Process -FilePath "$javaHome\bin\java.exe" `
    -ArgumentList $javaArgs `
    -WorkingDirectory $projectDir `
    -RedirectStandardOutput "$projectDir\start.log" `
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
    if (Test-Path "$projectDir\start.log") {
        $log = Get-Content "$projectDir\start.log" -Encoding UTF8 -ErrorAction SilentlyContinue
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

Write-Host "Startup check timed out (${maxWait}s). Check start.log manually." -ForegroundColor Yellow
