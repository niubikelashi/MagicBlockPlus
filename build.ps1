# Offline build for MagicBlockPlus (no Maven/Gradle on this machine).
$ErrorActionPreference = "Stop"
$root = "C:/Users/hui/Documents/Strata/Workspace/MagicBlockPlus"
$jdk = "C:/Program Files/Java/jdk-17.0.2/bin"
$libs = Get-ChildItem -Path "$root/libs" -Filter *.jar | ForEach-Object { $_.FullName.Replace("\", "/") }
$classpath = ($libs -join ";")

$classes = "$root/build/classes"
$stage = "$root/build/stage"
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
New-Item -ItemType Directory -Force -Path "$stage/io/github/niubikelashi/magicblockplus" | Out-Null

$sources = Get-ChildItem -Path "$root/src/main/java" -Recurse -Filter *.java | ForEach-Object { $_.FullName.Replace("\", "/") }
$argfile = "$root/build/javac.args"
$lines = @("-encoding", "UTF-8", "--release", "17", "-classpath", $classpath, "-d", $classes)
$lines += $sources
Set-Content -Path $argfile -Value $lines -Encoding ASCII

& "$jdk/javac.exe" "@$argfile"
if ($LASTEXITCODE -ne 0) { Write-Host "COMPILE FAILED"; exit 1 }
Write-Host "compiled: $($sources.Count) files"

Copy-Item "$root/src/main/resources/plugin.yml" "$stage/plugin.yml"
Copy-Item "$root/src/main/resources/config.yml" "$stage/config.yml"
Copy-Item "$root/src/main/resources/food.yml" "$stage/food.yml"
# Class files must sit at the jar root (io/github/...), NOT under classes/ --
# plain CraftBukkit/Spigot fails with "Cannot find main class" otherwise.
Get-ChildItem -Path $classes -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($classes.Length + 1).Replace("\", "/")
    $dest = Join-Path $stage $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $dest -Parent) | Out-Null
    Copy-Item -Force $_.FullName $dest
}

$version = "1.0.0"
New-Item -ItemType Directory -Force -Path "$root/dist" | Out-Null
$jarName = "MagicBlockPlus-$version.jar"
if (Test-Path "$root/dist/$jarName") { Remove-Item -Force "$root/dist/$jarName" }
Push-Location $stage
& "$jdk/jar.exe" "--create" "--file" "$root/dist/$jarName" "."
Pop-Location
if ($LASTEXITCODE -ne 0) { Write-Host "JAR FAILED"; exit 1 }

$size = (Get-Item "$root/dist/$jarName").Length
Write-Host "jar ok: $jarName ($size bytes)"
& "$jdk/javap.exe" -classpath "$classes" io.github.niubikelashi.magicblockplus.MagicBlockPlusPlugin | Select-Object -First 12
