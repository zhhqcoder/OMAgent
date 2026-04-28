# OMAgent 构建脚本
# 用法: .\build.ps1
# 功能: Maven打包 + 同步资源文件到config目录

$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

Write-Host "=== OMAgent Build ===" -ForegroundColor Cyan

# 1. Maven 打包
Write-Host "[1/2] Maven packaging..." -ForegroundColor Yellow
cd $projectDir
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Maven build failed!" -ForegroundColor Red
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
