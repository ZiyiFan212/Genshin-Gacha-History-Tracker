# Windows PowerShell 5.1 and PowerShell 7 on Windows.
[CmdletBinding()]
param(
    [string]$JarPath,
    [string]$AppRoot,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
try {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw 'This launcher supports Windows only.'
    }
    if (-not $JarPath) {
        $JarPath = Join-Path $PSScriptRoot 'Genshin-Analyzer-NEXT-1.0-SNAPSHOT.jar'
    }
    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw "JAR not found: $JarPath. Run gradlew.bat jar first, then use build\libs\launch.ps1."
    }
    $JarPath = (Resolve-Path -LiteralPath $JarPath).Path

    # NodeLocator resolves proxy/ and .gradle/nodejs relative to user.dir.
    if (-not $AppRoot) {
        foreach ($candidate in @($PSScriptRoot, (Join-Path $PSScriptRoot '..\..'))) {
            if (Test-Path -LiteralPath (Join-Path $candidate 'proxy\proxy.js') -PathType Leaf) {
                $AppRoot = $candidate
                break
            }
        }
    }
    if (-not $AppRoot -or -not (Test-Path -LiteralPath (Join-Path $AppRoot 'proxy\proxy.js') -PathType Leaf)) {
        throw 'Cannot locate proxy\proxy.js. Supply -AppRoot with the tracker project directory.'
    }
    $AppRoot = (Resolve-Path -LiteralPath $AppRoot).Path

    $javaPath = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        (Get-Command java.exe -ErrorAction Stop).Source
    }
    # Use stdout for version detection; java -version normally writes to stderr.
    $versionText = (& $javaPath --version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $versionText -notmatch '(?m)^(?:openjdk|java)\s+(\d+)') {
        throw "Unable to verify Java 25 or newer at $javaPath. Set JAVA_HOME to a compatible JDK."
    }
    if ([int]$Matches[1] -lt 25) {
        throw "Java 25 or newer is required. Selected runtime: $javaPath"
    }

    $javaArguments = "--enable-native-access=ALL-UNNAMED -jar `"$JarPath`""
    if ($CheckOnly) {
        [pscustomobject]@{ Java = $javaPath; Arguments = $javaArguments; WorkingDirectory = $AppRoot }
        return
    }

    $logDir = Join-Path $PSScriptRoot 'launcher-logs'
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    $logName = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $process = Start-Process -FilePath $javaPath -ArgumentList $javaArguments `
        -WorkingDirectory $AppRoot -WindowStyle Hidden -PassThru -Wait `
        -RedirectStandardOutput (Join-Path $logDir "$logName.stdout.log") `
        -RedirectStandardError (Join-Path $logDir "$logName.stderr.log")
    if ($process.ExitCode -ne 0) {
        throw "Java exited with code $($process.ExitCode). See $logDir."
    }
} catch {
    $message = "Genshin Analyzer launch failed: $($_.Exception.Message)"
    if (-not $CheckOnly) {
        Add-Type -AssemblyName System.Windows.Forms
        [System.Windows.Forms.MessageBox]::Show($message, 'Genshin Analyzer') | Out-Null
    }
    Write-Error $message
    exit 1
}
