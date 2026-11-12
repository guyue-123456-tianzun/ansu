# 安宿后端一键启动（PowerShell）
$ErrorActionPreference = "Stop"
Set-Location "$PSScriptRoot\ansu-server"
if (-not (Test-Path ".\pom.xml")) {
    Write-Error "找不到 ansu-server/pom.xml，请确认项目目录完整。"
}
mvn -f ".\pom.xml" spring-boot:run "-Dspring-boot.run.profiles=demo"
