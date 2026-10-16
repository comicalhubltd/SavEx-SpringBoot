param([switch]$CheckOnly, [ValidateSet('dev','xampp')][string]$Profile = 'dev')
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectPath '.env'
$allowed = @('OPENAI_API_KEY','SAVEX_AI_ENABLED','SAVEX_AI_MODEL','SAVEX_AI_DAILY_LIMIT','SAVEX_AI_LEARNING_ENABLED','DB_URL','DB_USER','DB_PASS','JWT_SECRET','MAIL_USER','MAIL_PASS','ADMIN_SETUP_TOKEN','SAVEX_MONEY_MODE','FLW_SECRET_KEY','FLW_PUBLIC_KEY','FLW_WEBHOOK_SECRET','SAVEX_PUBLIC_URL','FLW_TRANSFERS_APPROVED','FLW_BILLS_APPROVED','FLW_PAYOUT_WALLETS_ENABLED','FLW_IDENTITY_ENABLED','FLW_IDENTITY_MODE','FLW_IDENTITY_SECRET','SAVEX_LIVE_PILOT_ENABLED','SAVEX_LIVE_ALLOWED_USERS','SAVEX_LIVE_MAX_TRANSACTION_KOBO','SAVEX_LIVE_DAILY_LIMIT_KOBO')
if (Test-Path -LiteralPath $envPath) {
    $envLineNumber = 0
    foreach ($line in Get-Content -LiteralPath $envPath) {
        $envLineNumber++
        if ($line.Trim() -eq '' -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^\s*([A-Z_][A-Z0-9_]*)\s*=(.*)$') { throw "Invalid .env entry on line $envLineNumber. Use NAME=value without Markdown code-block markers. Values are not displayed." }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($allowed -notcontains $name) { continue }
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1,$value.Length-2)
        }
        if ($value -ne '') { [Environment]::SetEnvironmentVariable($name,$value,'Process') }
    }
}
$mode = $env:SAVEX_MONEY_MODE
if (!$mode) { $mode = 'OFF' }
if ($mode -notin @('OFF','TEST','LIVE')) { throw 'Payment mode must be OFF, TEST or LIVE.' }
if ($mode -eq 'LIVE' -and ($Profile -ne 'xampp' -or $env:SAVEX_LIVE_PILOT_ENABLED -ne 'true' -or !$env:SAVEX_LIVE_ALLOWED_USERS)) {
    throw 'LIVE requires -Profile xampp, SAVEX_LIVE_PILOT_ENABLED=true and a reviewed SAVEX_LIVE_ALLOWED_USERS list.'
}
if ($mode -eq 'LIVE') {
    $pilotMaximum = 1000000L
    $pilotDaily = 2000000L
    if ($env:SAVEX_LIVE_MAX_TRANSACTION_KOBO -and ![long]::TryParse($env:SAVEX_LIVE_MAX_TRANSACTION_KOBO,[ref]$pilotMaximum)) { throw 'Invalid live per-payment limit.' }
    if ($env:SAVEX_LIVE_DAILY_LIMIT_KOBO -and ![long]::TryParse($env:SAVEX_LIVE_DAILY_LIMIT_KOBO,[ref]$pilotDaily)) { throw 'Invalid live daily limit.' }
    if ($pilotMaximum -lt 100 -or $pilotMaximum -gt 1000000 -or $pilotDaily -lt $pilotMaximum -or $pilotDaily -gt 2000000) {
        throw 'Live funding and withdrawal limits must stay within NGN 10000 per payment and NGN 20000 per day per service, including the SavEx fee in daily usage. Limit settings use kobo.'
    }
}
if ($mode -eq 'TEST') {
    if (!$env:FLW_SECRET_KEY -or !$env:FLW_SECRET_KEY.StartsWith('FLWSECK_TEST-')) { throw 'Set a Flutterwave v3 test secret in .env.' }
    if (!$env:FLW_WEBHOOK_SECRET -or $env:FLW_WEBHOOK_SECRET.Length -lt 32) { throw 'Set FLW_WEBHOOK_SECRET to at least 32 characters in .env.' }
}
Write-Host "SavEx payment mode: $mode. Credentials are kept private."
if ($Profile -eq 'xampp' -and (!$env:DB_PASS -or !$env:JWT_SECRET)) { throw 'Persistent startup requires DB_PASS and a private JWT_SECRET in .env.' }
if ($Profile -eq 'xampp') {
    $databaseHost = '127.0.0.1'
    $databasePort = 3306
    if ($env:DB_URL -match '^jdbc:mysql://([^/:?]+)(?::(\d+))?/') {
        $databaseHost = $Matches[1]
        if ($Matches[2]) { $databasePort = [int]$Matches[2] }
    }
    $databaseSocket = [System.Net.Sockets.TcpClient]::new()
    try {
        $connectionTask = $databaseSocket.ConnectAsync($databaseHost,$databasePort)
        if (!$connectionTask.Wait(3000) -or !$databaseSocket.Connected) { throw 'Database connection unavailable.' }
    } catch {
        throw "MySQL is not reachable at ${databaseHost}:${databasePort}. Open XAMPP and start MySQL, then retry SavEx."
    } finally { $databaseSocket.Dispose() }
}
if ($CheckOnly) { return }
if (!(Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Install Maven and a JDK 21 or newer, then retry.' }
Push-Location $projectPath
try {
    # Rebuild both Java and browser resources so the permission rules match the page.
    $mavenCachePath = Join-Path $env:USERPROFILE '.m2/repository'
    & mvn clean spring-boot:run "-Dmaven.repo.local=$mavenCachePath" "-Dspring-boot.run.profiles=$Profile"
    if ($LASTEXITCODE -ne 0) { throw 'SavEx startup failed.' }
}
finally { Pop-Location }
