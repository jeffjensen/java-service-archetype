<#
.SYNOPSIS
  Interactive smoke test for java-service-archetype.

.DESCRIPTION
  Installs the archetype to the local Maven repo, then runs mvn archetype:generate
  in interactive mode by redirecting answers from a temp file into stdin.
  The generated project is written to a temp directory and removed on exit.

.PARAMETER Verify
  Also run `mvn verify -f parent/pom.xml` on the generated project (slow).

.EXAMPLE
  .\test-interactive.ps1
  .\test-interactive.ps1 -Verify
#>
param([switch]$Verify)

$ErrorActionPreference = 'Stop'

# Run native commands (Maven, the JVM) through this helper. They write warnings
# and progress to stderr; under $ErrorActionPreference='Stop', PowerShell turns
# that stderr into a TERMINATING error the moment the caller redirects streams
# (e.g. `.\test-interactive.ps1 *> run.log`). 'SilentlyContinue' makes the native
# stderr non-fatal AND keeps it out of a redirected log, while the real Maven
# errors (written to stdout) and the exit-code gate below still surface failures.
function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$What,
        [Parameter(Mandatory)][scriptblock]$Command
    )
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    try { & $Command } finally { $ErrorActionPreference = $prevEap }
    if ($LASTEXITCODE -ne 0) { throw "$What failed with exit code $LASTEXITCODE" }
}

$ArchetypeGroupId    = "io.github.jeffjensen"
$ArchetypeArtifactId = "java-service-archetype"
# $ArchetypeVersion is read from the project pom.xml at runtime (after install),
# so this script needs no edits when the project version changes.

$ScriptDir   = $PSScriptRoot
$OutDir      = Join-Path $env:TEMP "archetype-test-$(Get-Random)"
$AnswersFile = Join-Path $env:TEMP "archetype-answers-$(Get-Random).txt"

try {
    # ── 1. Install ─────────────────────────────────────────────────────────────
    Write-Host "==> Installing archetype to local repo..."
    Invoke-Native "mvn install" { & "$ScriptDir\mvnw.cmd" -f "$ScriptDir\pom.xml" install -q }

    # ── Read the archetype version straight from the project POM, so this script
    #    needs no maintenance when the project version changes ──────────────────
    # Use cmd /c with a single command string: PowerShell 5.1 mangles arguments
    # like "-Dexpression=project.version" when passing them to the mvnw.cmd batch
    # file, so let cmd.exe parse the line (same pattern as the generate step).
    $ArchetypeVersion = cmd /c "`"$ScriptDir\mvnw.cmd`" -f `"$ScriptDir\pom.xml`" help:evaluate -Dexpression=project.version -q -DforceStdout --no-transfer-progress 2>NUL"
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace("$ArchetypeVersion")) {
        throw "Could not determine archetype version from $ScriptDir\pom.xml"
    }
    $ArchetypeVersion = "$ArchetypeVersion".Trim()
    Write-Host "==> Using archetype version: $ArchetypeVersion"

    # ── 2. Write answers to a temp file ───────────────────────────────────────
    # Answers fed to each Maven prompt, in the exact order maven-archetype-plugin
    # 3.4.x prompts. Required properties WITHOUT a non-empty default are prompted
    # first, in archetype-metadata.xml order, THEN groupId / artifactId / package.
    # Properties with a non-empty default are NOT prompted, so they get no answer
    # line here: version (1.0.0-SNAPSHOT), serviceAreas (",") and presentationTypes
    # (rest). The "," default makes serviceAreas resolve to a single "service" module.
    #
    #   integrations  -> database:main
    #   groupId       -> com.example
    #   artifactId    -> my-service
    #   package       -> (Enter: accept default derived from groupId)
    #   confirm       -> Y
    $answers = @(
        "database:main",
        "com.example",
        "my-service",
        "",
        "Y"
    )
    # Write WITHOUT a BOM. Windows PowerShell 5.1 'Set-Content -Encoding UTF8'
    # prepends a UTF-8 BOM, which would corrupt the first answer (groupId).
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($AnswersFile, ($answers -join "`n") + "`n", $utf8NoBom)

    # ── 3. Generate ────────────────────────────────────────────────────────────
    New-Item -ItemType Directory -Path $OutDir | Out-Null
    Write-Host "==> Generating project in $OutDir..."

    Invoke-Native "archetype:generate" {
        cmd /c "cd /d `"$OutDir`" && mvn archetype:generate --no-transfer-progress -DarchetypeGroupId=$ArchetypeGroupId -DarchetypeArtifactId=$ArchetypeArtifactId -DarchetypeVersion=$ArchetypeVersion < `"$AnswersFile`""
    }

    # ── 4. Show generated layout ───────────────────────────────────────────────
    Write-Host ""
    Write-Host "==> Generated project layout:"
    $projectDir = Join-Path $OutDir "my-service"
    Get-ChildItem -Path $projectDir -Recurse -File |
        Sort-Object FullName |
        ForEach-Object { "  " + $_.FullName.Replace($OutDir + "\", "") }

    # ── 5. Optionally verify the generated project compiles and tests pass ─────
    if ($Verify) {
        Write-Host ""
        Write-Host "==> Running mvn verify on generated project..."
        Invoke-Native "mvn verify" {
            cmd /c "cd /d `"$projectDir`" && mvn verify -f parent/pom.xml --no-transfer-progress"
        }
        Write-Host "==> Build: PASSED"
    }

    Write-Host ""
    Write-Host "==> SUCCESS"
}
finally {
    if (Test-Path $OutDir -ErrorAction SilentlyContinue) {
        Remove-Item $OutDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path $AnswersFile -ErrorAction SilentlyContinue) {
        Remove-Item $AnswersFile -Force -ErrorAction SilentlyContinue
    }
}
