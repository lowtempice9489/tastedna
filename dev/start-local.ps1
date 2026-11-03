param([switch]$CheckOnly)

# 비밀값은 현재 실행 프로세스에서만 사용한다. 파일이나 사용자 환경변수에 저장하지 않는다.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

$jdkCandidates = @(
    (Join-Path $env:USERPROFILE '.jdks\temurin-21.0.11'),
    (Join-Path $env:USERPROFILE '.jdks\ms-21.0.12'),
    $env:JAVA_HOME
)
$selectedJdk = $null
foreach ($candidate in $jdkCandidates) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe'))) {
        $version = & (Join-Path $candidate 'bin\java.exe') --version 2>&1 | Select-Object -First 1
        if ("$version" -match '(^|[^\d])21(\.|[^\d]|$)') { $selectedJdk = $candidate; break }
    }
}
if (-not $selectedJdk) { throw 'Java 21 JDK not found. Set JAVA_HOME to your Java 21 installation.' }

Write-Host "Project: $projectRoot"
Write-Host "Java 21: $selectedJdk"
Write-Host 'Database: localhost:3306/tastedna (development database)'
Write-Host 'Profile: local | URL: http://localhost:8080'
Write-Host 'Startup applies pending Flyway migrations, including V13-V15.'
Write-Host 'Existing definitions and sessions are not deleted by V13-V15.'

$listeners = @(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)
Write-Host ("Port 8080 listening: " + ($listeners.Count -gt 0))
foreach ($key in 'DB_USERNAME','DB_PASSWORD','DEV_MEMBER_PASSWORD') {
    Write-Host ("$key configured in this process: " + [bool][Environment]::GetEnvironmentVariable($key, 'Process'))
}
if ($CheckOnly) { exit 0 }
if ($listeners.Count -gt 0) {
    Write-Host 'Port 8080 is already in use. Open the existing server or inspect its process before starting another.'
    exit 1
}

$original = @{}
foreach ($key in 'JAVA_HOME','DB_USERNAME','DB_PASSWORD','DEV_MEMBER_PASSWORD') {
    $original[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
function Read-PrivateValue([string]$Prompt) {
    $secret = Read-Host $Prompt -AsSecureString
    try { return [System.Net.NetworkCredential]::new('', $secret).Password }
    finally { $secret.Dispose() }
}

try {
    $env:JAVA_HOME = $selectedJdk
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = Read-Host 'MySQL username (your existing database account)' }
    if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = Read-PrivateValue 'MySQL password (hidden input)' }
    if (-not $env:DEV_MEMBER_PASSWORD) {
        $env:DEV_MEMBER_PASSWORD = Read-PrivateValue 'Local demo login password (hidden input; used for local demo accounts)'
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or
        [string]::IsNullOrWhiteSpace($env:DB_PASSWORD) -or
        [string]::IsNullOrWhiteSpace($env:DEV_MEMBER_PASSWORD)) {
        throw 'All three values are required. Nothing has been saved.'
    }
    Write-Host ''
    Write-Host 'Starting TasteDNA. Keep this window open; Ctrl+C stops the server.'
    Write-Host 'Wait for: Started TastednaApplication'
    Write-Host 'Then open http://localhost:8080'
    & (Join-Path $projectRoot 'gradlew.bat') --offline bootRun '--args=--spring.profiles.active=local --logging.file.name=build/local-server.log'
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Startup failed. Inspect the first Caused by / ERROR and build/local-server.log.'
        exit $LASTEXITCODE
    }
}
finally {
    foreach ($key in $original.Keys) {
        [Environment]::SetEnvironmentVariable($key, $original[$key], 'Process')
    }
}

