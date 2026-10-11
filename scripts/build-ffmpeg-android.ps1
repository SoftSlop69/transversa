# SPDX-License-Identifier: GPL-3.0-or-later
$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "build-ffmpeg-android.sh"
if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    throw "WSL2 is required. Install/start the Ubuntu WSL2 distribution, then retry."
}

$normalizedScriptPath = $scriptPath -replace '\\', '/'
$wslScript = & wsl.exe wslpath -u $normalizedScriptPath
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($wslScript)) {
    throw "Could not resolve the repository script path inside WSL."
}
$wslScript = $wslScript.Trim()

& wsl.exe -- bash $wslScript
if ($LASTEXITCODE -ne 0) {
    throw "FFmpeg Android build failed in WSL with exit code $LASTEXITCODE."
}
