# Roguelike Dungeons Arcana: Gradle build (+ DEVBOX deploy) on the JDK 25 toolchain.
# Gradle 9 needs Java 17+ to run, so this sets JAVA_HOME instead of using the shell's (JDK 8) one.
#
#   .\scripts\build_gradle.ps1                     build, verify and deploy the remapped jar to DEVBOX
#   .\scripts\build_gradle.ps1 -NoDeploy           build and verify only
#   .\scripts\build_gradle.ps1 -Task test          run other Gradle tasks instead of the defaults
#   .\scripts\build_gradle.ps1 --warning-mode all  anything else is passed to Gradle as options
#
# Override with ROGUELIKE_JAVA_HOME when this machine differs. The DEVBOX mods folder is
# overridden with '-Pdevbox.mods.dir=...' (quoted, or PowerShell splits it at the dot).
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$NoDeploy,
    [string[]]$Task,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs
)
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

$env:JAVA_HOME = if ($env:ROGUELIKE_JAVA_HOME) { $env:ROGUELIKE_JAVA_HOME } else { "C:\Program Files\Zulu\zulu-25" }
Write-Output "JAVA_HOME: $env:JAVA_HOME"

if (-not $Task) {
    $Task = if ($NoDeploy) { @("build") } else { @("build", ":1.12:deployToDevbox") }
}
$Tasks = @($Task) + @($GradleArgs | Where-Object { $_ })

$oldPwd = Get-Location
Set-Location $repoRoot
$priorGradleOpts = $env:GRADLE_OPTS
$env:GRADLE_OPTS = (($priorGradleOpts, "--enable-native-access=ALL-UNNAMED") | Where-Object { $_ }) -join " "
$priorErrorPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & .\gradlew.bat @Tasks
    $gradleExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $priorErrorPreference
    $env:GRADLE_OPTS = $priorGradleOpts
    Set-Location $oldPwd
}
if ($gradleExit -ne 0) { throw "Gradle failed with exit code $gradleExit" }
Write-Output "=== Gradle succeeded: $($Tasks -join ' ') ==="
