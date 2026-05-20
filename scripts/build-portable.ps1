param(
    [string]$OutputDirectory = "",
    [string]$ShortcutPath = "",
    [switch]$NoShortcut,
    [switch]$TestLaunch
)

$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host ""
    Write-Host "==> $Name" -ForegroundColor Cyan
    & $Action
}

function Invoke-External {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDirectory = Resolve-Path (Join-Path $scriptDirectory "..")
Set-Location $projectDirectory

$desktopDirectory = [Environment]::GetFolderPath("Desktop")
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $desktopDirectory "e\FocusTime"
}
if ([string]::IsNullOrWhiteSpace($ShortcutPath)) {
    $ShortcutPath = Join-Path $desktopDirectory "FocusTime.lnk"
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven is not available in PATH."
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage is not available in PATH. It is included with the JDK."
}

$pom = [xml](Get-Content (Join-Path $projectDirectory "pom.xml"))
$artifactId = $pom.project.artifactId
$version = $pom.project.version
$appName = $pom.project.name
if ([string]::IsNullOrWhiteSpace($appName)) {
    $appName = "FocusTime"
}

$mainJar = "$artifactId-$version.jar"
$distDirectory = Join-Path $projectDirectory "target\dist"
$jpackageDestination = Join-Path $projectDirectory "target\app"
$generatedAppDirectory = Join-Path $jpackageDestination $appName
$generatedExe = Join-Path $generatedAppDirectory "$appName.exe"
$outputExe = Join-Path $OutputDirectory "$appName.exe"
$iconPath = Join-Path $projectDirectory "src\main\resources\com\focustime\icon.ico"
$javaFxIconPath = Join-Path $projectDirectory "src\main\resources\com\focustime\icon.png"
$outputIcon = $null
if (Test-Path $iconPath) {
    $iconHash = (Get-FileHash $iconPath -Algorithm SHA256).Hash.Substring(0, 12).ToLowerInvariant()
    $outputIcon = Join-Path $OutputDirectory "$appName-$iconHash.ico"
}

Invoke-Step "Checking running application" {
    $running = Get-Process -Name $appName -ErrorAction SilentlyContinue
    if ($running) {
        throw "$appName is running. Close the application before replacing the portable build."
    }
}

Invoke-Step "Synchronizing JavaFX window icon" {
    if (Test-Path $iconPath) {
        $shouldGeneratePng = -not (Test-Path $javaFxIconPath)
        if (-not $shouldGeneratePng) {
            $shouldGeneratePng = (Get-Item $iconPath).LastWriteTime -gt (Get-Item $javaFxIconPath).LastWriteTime
        }

        if ($shouldGeneratePng) {
            Add-Type -AssemblyName System.Drawing
            $icon = New-Object System.Drawing.Icon($iconPath)
            try {
                $bitmap = $icon.ToBitmap()
                try {
                    $bitmap.Save($javaFxIconPath, [System.Drawing.Imaging.ImageFormat]::Png)
                    Write-Host "Generated JavaFX icon: $javaFxIconPath"
                } finally {
                    $bitmap.Dispose()
                }
            } finally {
                $icon.Dispose()
            }
        } else {
            Write-Host "JavaFX icon is already up to date: $javaFxIconPath"
        }
    } else {
        Write-Host "No icon.ico found. Skipping JavaFX icon synchronization."
    }
}

Invoke-Step "Compiling and copying runtime dependencies" {
    Invoke-External "mvn" @(
        "-q",
        "clean",
        "package",
        "dependency:copy-dependencies",
        "-DincludeScope=runtime",
        "-DoutputDirectory=target\dist"
    )
}

Invoke-Step "Preparing distribution folder" {
    Copy-Item `
        -Path (Join-Path $projectDirectory "target\$mainJar") `
        -Destination (Join-Path $distDirectory $mainJar) `
        -Force

    if (Test-Path $jpackageDestination) {
        Remove-Item -LiteralPath $jpackageDestination -Recurse -Force
    }
}

Invoke-Step "Generating portable application image" {
    $jpackageArguments = @(
        "--type", "app-image",
        "--name", $appName,
        "--input", $distDirectory,
        "--main-jar", $mainJar,
        "--main-class", "com.focustime.FocusTimeLauncher",
        "--dest", $jpackageDestination
    )

    if (Test-Path $iconPath) {
        Write-Host "Using application icon: $iconPath"
        $jpackageArguments += @("--icon", $iconPath)
    }

    Invoke-External "jpackage" $jpackageArguments
}

Invoke-Step "Replacing portable folder" {
    if (-not (Test-Path $generatedExe)) {
        throw "Expected executable was not generated: $generatedExe"
    }

    if (Test-Path $OutputDirectory) {
        Remove-Item -LiteralPath $OutputDirectory -Recurse -Force
    }

    Copy-Item -Path $generatedAppDirectory -Destination $OutputDirectory -Recurse -Force

    if ($outputIcon) {
        Copy-Item -Path $iconPath -Destination $outputIcon -Force
    }
}

if (-not $NoShortcut) {
    Invoke-Step "Creating desktop shortcut" {
        if (Test-Path $ShortcutPath) {
            Remove-Item -LiteralPath $ShortcutPath -Force
        }

        $shell = New-Object -ComObject WScript.Shell
        $shortcut = $shell.CreateShortcut($ShortcutPath)
        $shortcut.TargetPath = $outputExe
        $shortcut.WorkingDirectory = $OutputDirectory
        if ($outputIcon -and (Test-Path $outputIcon)) {
            $shortcut.IconLocation = $outputIcon
        }
        $shortcut.Save()
    }
}

if ($TestLaunch) {
    Invoke-Step "Testing executable startup" {
        $process = Start-Process `
            -FilePath $outputExe `
            -WorkingDirectory $OutputDirectory `
            -PassThru

        Start-Sleep -Seconds 5
        if ($process.HasExited) {
            throw "$appName exited during startup with code $($process.ExitCode)."
        }

        Stop-Process -Id $process.Id -Force
        Write-Host "$appName started successfully." -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "Portable build ready:" -ForegroundColor Green
Write-Host "  $OutputDirectory"
Write-Host "Executable:"
Write-Host "  $outputExe"
if (-not $NoShortcut) {
    Write-Host "Shortcut:"
    Write-Host "  $ShortcutPath"
}
