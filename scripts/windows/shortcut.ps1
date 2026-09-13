[CmdletBinding()]
param(
    [string]$OutputDirectory = [Environment]::GetFolderPath('Desktop'),
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'Shortcut creation supports Windows only.'
}
$launchPath = Join-Path $PSScriptRoot 'launch.ps1'
if (-not (Test-Path -LiteralPath $launchPath -PathType Leaf)) {
    throw "Cannot find launch.ps1: $launchPath"
}
# Validate the JAR, runtime and working directory before installing a shortcut.
$launchPlan = & $launchPath -CheckOnly
if (-not $launchPlan) { throw 'Launcher validation failed.' }
$powershellPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$shortcutPath = Join-Path $OutputDirectory 'Genshin Analyzer.lnk'
$arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$launchPath`""
if ($CheckOnly) {
    [pscustomobject]@{ Shortcut = $shortcutPath; Target = $powershellPath; Arguments = $arguments; WorkingDirectory = $launchPlan.WorkingDirectory }
    return
}
if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
    throw "Shortcut directory does not exist: $OutputDirectory"
}
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $powershellPath
$shortcut.Arguments = $arguments
$shortcut.WorkingDirectory = $launchPlan.WorkingDirectory
$shortcut.Description = 'Genshin Analyzer'
$iconPath = Join-Path $PSScriptRoot 'app.ico'
if (Test-Path -LiteralPath $iconPath -PathType Leaf) {
    $shortcut.IconLocation = "$iconPath,0"
}
$shortcut.Save()
Write-Output "Created shortcut: $shortcutPath"
