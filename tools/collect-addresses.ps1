# Collects groups of addresses whose operator answers only connections from Ukraine (Poltava).
# Run it on a computer in Ukraine whenever there is power; it continues where it stopped.
#   powershell -ExecutionPolicy Bypass -File tools\collect-addresses.ps1 [-Hours 2]
param([double]$Hours = 2, [string]$Crawler = "poltava")
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$cache = Join-Path (Split-Path -Parent $repo) "koly-cache"

$py = if (Get-Command py -ErrorAction SilentlyContinue) { "py" } elseif (Get-Command python -ErrorAction SilentlyContinue) { "python" } else { $null }
if (-not $py) { Write-Host "Python is not installed: get it from https://www.python.org/downloads/ (tick 'Add to PATH')."; exit 1 }

Write-Host "Updating..."
git -C $repo pull --quiet
& $py -m pip install --quiet --disable-pip-version-check -r (Join-Path $repo "data\requirements.txt") -r (Join-Path $repo "data\houses\requirements.txt")

git -C $repo fetch --quiet origin houses-cache
if (Test-Path $cache) { git -C $cache checkout --quiet --detach origin/houses-cache }
else { git -C $repo worktree add --quiet --detach $cache origin/houses-cache }

Write-Host "Collecting for $Hours h. You can stop with Ctrl+C; what is found so far is kept."
Push-Location (Join-Path $repo "data")
try { & $py -m houses.local --cache (Join-Path $cache "cache") --crawler $Crawler --hours $Hours }
finally { Pop-Location }

git -C $cache add cache
git -C $cache diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
  git -C $cache commit --quiet -m "houses: $Crawler"
  git -C $cache push --quiet origin HEAD:houses-cache
  Write-Host "Saved. The map picks it up within a couple of hours."
} else { Write-Host "Nothing new." }
