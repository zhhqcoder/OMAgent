# OMAgent 构建脚本
# 用法: .\scripts\build.ps1 （从项目根目录执行）
# 功能: Maven打包 + 同步资源文件到config目录

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$logDir = "$projectDir\logs"

# 确保日志目录存在
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

Write-Host "=== OMAgent Build ===" -ForegroundColor Cyan

# 1. Maven 打包
Write-Host "[1/2] Maven packaging..." -ForegroundColor Yellow
cd $projectDir
mvn clean package -DskipTests 2>&1 | Tee-Object -FilePath "$logDir\build.log"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed! See logs\build.log for details." -ForegroundColor Red
    exit 1
}

# 2. 同步资源文件到 config 目录（外部配置，jar包外）
Write-Host "[2/2] Sync resources to config/..." -ForegroundColor Yellow
New-Item -ItemType Directory -Path "$projectDir\config\prompts" -Force | Out-Null

# 复制配置文件
Copy-Item -Path "$projectDir\src\main\resources\application.yml" -Destination "$projectDir\config\application.yml" -Force
Copy-Item -Path "$projectDir\src\main\resources\application-dev.yml" -Destination "$projectDir\config\application-dev.yml" -Force

# 复制提示词文件
Copy-Item -Path "$projectDir\src\main\resources\prompts\*" -Destination "$projectDir\config\prompts\" -Force

Write-Host "Build completed successfully!" -ForegroundColor Green
Write-Host "  JAR: target\omagent-1.0.0.jar"
Write-Host "  Config: config\application.yml"
Write-Host "  Prompts: config\prompts\"
