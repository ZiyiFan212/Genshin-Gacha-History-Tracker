[CmdletBinding()]
param(
    [string]$OutputDirectory = [Environment]::GetFolderPath('Desktop'),
    [string]$IconPath,
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
# Windows shortcuts need an ICO file; PNG/JPEG artwork is converted below.
if (-not $IconPath) {
    foreach ($candidate in @('app.png', 'app.jpg', 'app.jpeg', 'app.ico')) {
        $candidatePath = Join-Path $PSScriptRoot $candidate
        if (Test-Path -LiteralPath $candidatePath -PathType Leaf) {
            $IconPath = $candidatePath
            break
        }
    }
}
$shortcutIcon = $null
if ($IconPath) {
    $IconPath = (Resolve-Path -LiteralPath $IconPath).Path
    $extension = [IO.Path]::GetExtension($IconPath).ToLowerInvariant()
    if ($extension -notin @('.png', '.jpg', '.jpeg', '.ico')) { throw 'The icon must be a PNG, JPEG or ICO file.' }
    $shortcutIcon = if ($extension -ne '.ico') { Join-Path $PSScriptRoot 'app.ico' } else { $IconPath }
}
if ($CheckOnly) {
    [pscustomobject]@{ Shortcut = $shortcutPath; Target = $powershellPath; Arguments = $arguments; WorkingDirectory = $launchPlan.WorkingDirectory; IconSource = $IconPath; IconLocation = $shortcutIcon }
    return
}
if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
    throw "Shortcut directory does not exist: $OutputDirectory"
}
if ($IconPath -and $extension -ne '.ico') {
    Add-Type -AssemblyName System.Drawing
    $sourceImage = [Drawing.Image]::FromFile($IconPath)
    $iconStream = New-Object IO.MemoryStream
    $writer = New-Object IO.BinaryWriter($iconStream)
    try {
        # Store a transparent 256px PNG frame inside the Windows ICO container.
        $bitmap = New-Object Drawing.Bitmap(256, 256)
        try {
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([Drawing.Color]::Transparent)
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $scale = [Math]::Min(256.0 / $sourceImage.Width, 256.0 / $sourceImage.Height)
                $width = [Math]::Max(1, [int][Math]::Round($sourceImage.Width * $scale))
                $height = [Math]::Max(1, [int][Math]::Round($sourceImage.Height * $scale))
                $graphics.DrawImage($sourceImage, [int]((256 - $width) / 2), [int]((256 - $height) / 2), $width, $height)
            } finally { $graphics.Dispose() }
            $pngStream = New-Object IO.MemoryStream
            try {
                $bitmap.Save($pngStream, [Drawing.Imaging.ImageFormat]::Png)
                $pngBytes = $pngStream.ToArray()
            } finally { $pngStream.Dispose() }
        } finally { $bitmap.Dispose() }
        $writer.Write([uint16]0) # Reserved
        $writer.Write([uint16]1) # ICO
        $writer.Write([uint16]1) # One image
        $writer.Write([byte]0)   # Width 256
        $writer.Write([byte]0)   # Height 256
        $writer.Write([byte]0)   # Palette
        $writer.Write([byte]0)   # Reserved
        $writer.Write([uint16]1) # Planes
        $writer.Write([uint16]32)
        $writer.Write([uint32]$pngBytes.Length)
        $writer.Write([uint32]22) # Header plus directory entry
        $writer.Write([byte[]]$pngBytes)
        $writer.Flush()
        [IO.File]::WriteAllBytes($shortcutIcon, $iconStream.ToArray())
    } finally {
        $sourceImage.Dispose()
        $writer.Dispose()
        $iconStream.Dispose()
    }
}
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $powershellPath
$shortcut.Arguments = $arguments
$shortcut.WorkingDirectory = $launchPlan.WorkingDirectory
$shortcut.Description = 'Genshin Analyzer'
if ($shortcutIcon) {
    $shortcut.IconLocation = "$shortcutIcon,0"
} else {
    Write-Warning 'No app.png, app.jpg, app.jpeg or app.ico found beside the launcher. The shortcut will use the default icon.'
}
$shortcut.Save()
Write-Output "Created shortcut: $shortcutPath"
