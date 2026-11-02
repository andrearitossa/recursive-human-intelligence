# Installs RHI for the current user, with no Java needed. Windows.
$ErrorActionPreference = 'Stop'
$repo = 'andrearitossa/recursive-human-intelligence'
$url = if ($env:RHI_ARCHIVE) { $env:RHI_ARCHIVE } else { "https://github.com/$repo/releases/latest/download/RHI-windows-x64.zip" }
$temp = Join-Path $env:TEMP ("rhi-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $temp | Out-Null
try {
  Write-Host 'Downloading RHI...'
  if (Test-Path $url) { Copy-Item $url "$temp\rhi.zip" } else { Invoke-WebRequest $url -OutFile "$temp\rhi.zip" -UseBasicParsing }
  Expand-Archive "$temp\rhi.zip" -DestinationPath $temp
  $target = Join-Path $env:LOCALAPPDATA 'RHI'
  if (Test-Path $target) { Remove-Item $target -Recurse -Force }
  Move-Item "$temp\RHI" $target
  $shell = New-Object -ComObject WScript.Shell
  $link = $shell.CreateShortcut((Join-Path ([Environment]::GetFolderPath('Programs')) 'RHI.lnk'))
  $link.TargetPath = "$target\RHI.exe"; $link.Save()
  Write-Host "RHI is installed. Find it in the Start menu, or run: $target\RHI.exe"
} finally { Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue }
