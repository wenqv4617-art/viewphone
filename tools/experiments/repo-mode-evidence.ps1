# ============================================================
# DEC-008 evidence experiment: switch repositoriesMode and restore.
#
# Purpose: make the DEC-008 conclusion reproducible without hand-editing settings.
# This script ONLY rewrites settings.gradle.kts; it does NOT invoke Gradle,
# because how a shell passes a full command line to cmd.exe differs between
# hosts (we hit exit code 9009 in one host for `cmd /c "gradlew.bat --version"`).
# Printing the exact commands keeps the experiment host-independent.
#
# Usage (from repo root):
#   powershell -ExecutionPolicy Bypass -File tools/experiments/repo-mode-evidence.ps1
#   powershell -ExecutionPolicy Bypass -File tools/experiments/repo-mode-evidence.ps1 -Mode FAIL_ON_PROJECT_REPOS
#   powershell -ExecutionPolicy Bypass -File tools/experiments/repo-mode-evidence.ps1 -Restore
#
# Evidence logs produced manually (kept in .buildlogs/, gitignored):
#   exp1-fail-on-project-repos.log   -> "...was added by unknown code"
#   exp2-prefer-settings.log         -> "Could not find org.nodejs:node:24.16.0"
#
# TRAPS ALREADY HIT AND FIXED -- do not regress:
#   1. The replacement must match the WHOLE call
#      repositoriesMode.set(RepositoriesMode.X).
#      Replacing only "RepositoriesMode.X" eats the method name and voids the run.
#   2. KEEP THIS FILE PURE ASCII. Windows PowerShell 5.1 reads .ps1 as ANSI when
#      there is no BOM, so UTF-8 CJK comments get mangled and break quoting.
#      Chinese explanation belongs in docs/DECISIONS.md, not here.
# ============================================================
param(
  [ValidateSet('PREFER_PROJECT', 'PREFER_SETTINGS', 'FAIL_ON_PROJECT_REPOS')]
  [string]$Mode,
  [switch]$Restore
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$settings = Join-Path $root 'settings.gradle.kts'
# Set-Location only moves $PWD; native tools use the PROCESS current directory.
[System.IO.Directory]::SetCurrentDirectory($root)
Set-Location $root

$pattern = 'repositoriesMode\.set\(RepositoriesMode\.[A-Z_]+\)'

if ($Restore) {
  $text = Get-Content $settings -Raw -Encoding UTF8
  $text = $text -replace $pattern, 'repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)'
  [System.IO.File]::WriteAllText($settings, $text, (New-Object System.Text.UTF8Encoding($false)))
  Write-Host "restored to PREFER_PROJECT"
  Select-String -Path $settings -Pattern 'repositoriesMode\.set' | ForEach-Object { Write-Host ("  " + $_.Line.Trim()) }
  exit 0
}

if (-not $Mode) {
  Write-Host "Usage:"
  Write-Host "  -Mode PREFER_PROJECT | PREFER_SETTINGS | FAIL_ON_PROJECT_REPOS"
  Write-Host "  -Restore        (put it back to PREFER_PROJECT)"
  Write-Host ""
  Write-Host "Then run the experiment yourself and capture the output, e.g.:"
  Write-Host "  .\gradlew.bat :kotlinNodeJsSetup --console=plain"
  exit 0
}

$text = Get-Content $settings -Raw -Encoding UTF8
$text = $text -replace $pattern, "repositoriesMode.set(RepositoriesMode.$Mode)"
[System.IO.File]::WriteAllText($settings, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "repositoriesMode set to $Mode"
Select-String -Path $settings -Pattern 'repositoriesMode\.set' | ForEach-Object { Write-Host ("  " + $_.Line.Trim()) }
Write-Host ""
Write-Host "Now run (and capture): .\gradlew.bat :kotlinNodeJsSetup --console=plain"
Write-Host "Expected failure signature:"
switch ($Mode) {
  'FAIL_ON_PROJECT_REPOS' { Write-Host "  'Distributions at https://nodejs.org/dist' was added by unknown code" }
  'PREFER_SETTINGS'       { Write-Host "  Could not find org.nodejs:node:24.16.0" }
  default                 { Write-Host "  (this is the working mode: expect BUILD SUCCESSFUL)" }
}
Write-Host ""
Write-Host "Remember to restore afterwards: -Restore"
