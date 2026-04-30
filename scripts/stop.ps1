# OMAgent 停止脚本
# 用法: .\scripts\stop.ps1 （从项目根目录执行）

$process = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($process) {
    Write-Host "Stopping Java process (PID: $($process.Id))..." -ForegroundColor Yellow
    $process | Stop-Process -Force
    Start-Sleep -Seconds 2
    Write-Host "Stopped." -ForegroundColor Green
} else {
    Write-Host "No Java process running." -ForegroundColor Gray
}
