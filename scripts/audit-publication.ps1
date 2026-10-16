param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = 'Stop'
$auditRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$sourceRoots = @('app/src', 'app/schemas', 'core/domain/src', 'gradle', '.github', 'docs', 'scripts')
$extensions = @('.kt', '.java', '.kts', '.xml', '.json', '.toml', '.yml', '.yaml', '.md', '.properties', '.ps1', '.txt')
$candidateFiles = @()
foreach ($relativeRoot in $sourceRoots) {
    $sourcePath = Join-Path $auditRoot $relativeRoot
    if (Test-Path -LiteralPath $sourcePath) {
        $candidateFiles += Get-ChildItem -LiteralPath $sourcePath -File -Recurse
    }
}
$candidateFiles += Get-ChildItem -LiteralPath $auditRoot -File | Where-Object {
    $_.Name -ne 'local.properties' -and $_.Extension -ne '.ttml'
}
$candidateFiles = @($candidateFiles | Sort-Object FullName -Unique)
$rules = @{
    'Google API key' = 'AIza[0-9A-Za-z_-]{35}'
    'GitHub token' = '(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})'
    'Private key' = '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
    'JWT literal' = 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
    'Bearer literal' = '(?i)Bearer\s+[A-Za-z0-9._~-]{24,}'
    'Secret assignment' = '(?i)(?:api[_-]?key|client[_-]?secret|access[_-]?token|refresh[_-]?token|password)\s*[:=]\s*["''][^"''\r\n]{16,}["'']'
    'Private user path' = '[A-Za-z]:[/\\]+Users[/\\]+[^/\\\s"'']+'
}
$findings = @()
foreach ($file in $candidateFiles) {
    $relative = $file.FullName.Substring($auditRoot.Length + 1).Replace('\', '/')
    if ($file.Extension -in @('.jks', '.keystore', '.p12', '.pem', '.key') -or
        $file.Name -in @('signing.properties', 'keystore.properties', 'secrets.properties') -or
        ($file.Name -like '.env*' -and $file.Name -ne '.env.example')) {
        $findings += [pscustomobject]@{ file = $relative; line = 0; category = 'Private configuration or signing file' }
        continue
    }
    if ($file.Extension -notin $extensions) { continue }
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadLines($file.FullName)) {
        $lineNumber++
        foreach ($rule in $rules.GetEnumerator()) {
            $matches = [regex]::Matches($line, $rule.Value)
            foreach ($match in $matches) {
                # Explicit demo markers are not production credentials. Never print matched values.
                if ($rule.Key -ne 'Private user path' -and $match.Value -match '(?i)example|placeholder|redacted|dummy|your[_-]') { continue }
                $findings += [pscustomobject]@{ file = $relative; line = $lineNumber; category = $rule.Key }
            }
        }
    }
}
if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize
    Write-Output "Publication candidate audit: $($findings.Count) finding(s); matched values are intentionally hidden."
    exit 1
}
Write-Output "Publication candidate audit: $($candidateFiles.Count) files, no matches for the configured secret/private-path rules."
Write-Output 'Scope: intended source/documentation roots and root files; generated/private directories and the personal TTML import are excluded.'
Write-Output 'This is a heuristic source scan, not a security certificate, license clearance or Git-history audit.'
