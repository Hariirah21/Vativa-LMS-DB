param(
    [switch]$KeepEnvironment
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $projectDirectory

$securePassword = Read-Host 'Enter the current Neon database password' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $env:VATIVA_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

if ([string]::IsNullOrWhiteSpace($env:VATIVA_JWT_SECRET)) {
    $randomBytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
    $env:VATIVA_JWT_SECRET = [Convert]::ToBase64String($randomBytes)
}

Write-Host 'Starting Vativa LMS with the Neon profile on port 8081...'

try {
    & mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=neon'
    exit $LASTEXITCODE
} finally {
    if (-not $KeepEnvironment) {
        Remove-Item Env:VATIVA_DB_PASSWORD -ErrorAction SilentlyContinue
    }
}
