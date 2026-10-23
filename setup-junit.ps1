$ErrorActionPreference = 'Stop'
$folder = Join-Path $PSScriptRoot 'lib'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$dest = Join-Path $folder 'junit-platform-console-standalone-6.0.3.jar'
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/6.0.3/junit-platform-console-standalone-6.0.3.jar' -OutFile "$dest.download"
if ((Get-FileHash -LiteralPath "$dest.download" -Algorithm SHA256).Hash.ToLowerInvariant() -ne '3ba0d6150af79214a1411f9ea2fbef864eef68b68c89a17f672c0b89bff9d3a2') { throw 'JUnit checksum mismatch; dependency not installed' }
Move-Item -LiteralPath "$dest.download" -Destination $dest -Force
Write-Output 'JUnit 6.0.3 cached and SHA-256 verified.'
