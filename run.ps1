param([ValidateSet('compile','run','check','test','lint','smoke')][string]$Task='compile', [Parameter(ValueFromRemainingArguments=$true)][string[]]$ProgramArgs)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdkRoot = $env:COURSE_JAVA_HOME
    if (-not $jdkRoot) { $jdkRoot = $env:JAVA_HOME }
    $compiler = 'javac'
    $runner = 'java'
    if ($jdkRoot) { $compiler = Join-Path $jdkRoot 'bin/javac'; $runner = Join-Path $jdkRoot 'bin/java' }
    if (-not (Get-Command $compiler -ErrorAction SilentlyContinue)) { throw 'JDK 21 is required. Set COURSE_JAVA_HOME or add its bin directory to PATH.' }
    $buildRoot = Join-Path $PSScriptRoot 'build'
    if (Test-Path -LiteralPath $buildRoot) {
        $resolvedBuild = (Resolve-Path -LiteralPath $buildRoot).Path
        if ($resolvedBuild -ne [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build'))) { throw 'Unexpected build directory' }
        Remove-Item -LiteralPath $resolvedBuild -Recurse -Force
    }
    New-Item -ItemType Directory -Path $buildRoot | Out-Null
    $sources = @(Get-ChildItem -LiteralPath 'src' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    & $compiler --release 21 -encoding UTF-8 -Xlint:all -d build @sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($Task -eq 'compile' -or $Task -eq 'lint') { Write-Output 'COMPILE_OK (warnings, if any, are printed above)'; exit 0 }
    if ($Task -eq 'run') { & $runner -cp build Main @ProgramArgs; exit $LASTEXITCODE }
    $plain = @(Get-ChildItem -LiteralPath 'test/plain' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    & $compiler --release 21 -encoding UTF-8 -Xlint:all -cp build -d build @plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($Task -eq 'smoke') { & $runner -cp build InfrastructureChecks; exit $LASTEXITCODE }
    if ($Task -eq 'check') { & $runner -cp build PublicChecks; exit $LASTEXITCODE }
    $jar = $env:JUNIT_JAR
    if (-not $jar) { $jar = Join-Path $PSScriptRoot 'lib/junit-platform-console-standalone-6.0.3.jar' }
    if (-not (Test-Path -LiteralPath $jar)) { throw 'JUnit not cached. Run ./setup-junit.ps1 once, or set JUNIT_JAR to the TA-provided 6.0.3 jar.' }
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne '3ba0d6150af79214a1411f9ea2fbef864eef68b68c89a17f672c0b89bff9d3a2') { throw 'JUnit checksum does not match pinned 6.0.3 dependency' }
    $tests = @(Get-ChildItem -LiteralPath 'test/junit' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    & $compiler --release 21 -encoding UTF-8 -cp "build$([IO.Path]::PathSeparator)$jar" -d build @tests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $runner -jar $jar execute --class-path build --scan-class-path --fail-if-no-tests --disable-ansi-colors --details summary
    exit $LASTEXITCODE
} finally { Pop-Location }
