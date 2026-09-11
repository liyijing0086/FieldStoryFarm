$ErrorActionPreference = 'Stop'
$files = Get-ChildItem -Recurse -Include *.md,*.txt -File | Where-Object { $_.FullName -notmatch '\\target\\' -and $_.FullName -notmatch '\\.git\\' }
$out = 'D:\code\Java\FieldStoryFarm\_utf8\'
New-Item -ItemType Directory -Force -Path $out | Out-Null
foreach ($f in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
    $enc = $null
    if ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) { $enc = [System.Text.Encoding]::Unicode }
    elseif ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFE -and $bytes[1] -eq 0xFF) { $enc = [System.Text.Encoding]::BigEndianUnicode }
    elseif ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { $enc = [System.Text.Encoding]::UTF8 }
    else {
        try { $enc = New-Object System.Text.UTF8Encoding($false, $true); $null = $enc.GetString($bytes) }
        catch { $enc = [System.Text.Encoding]::GetEncoding(936) }
    }
    if ($enc -is [System.Text.UTF8Encoding] -and $enc.GetPreamble().Length -eq 0) {
        # strict utf8 already decoded fine
    }
    $text = $enc.GetString($bytes)
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { $text = $text.Substring(1) }
    $rel = $f.FullName.Substring('D:\code\Java\FieldStoryFarm\'.Length)
    $dest = $out + ($rel -replace '[\\]', '__')
    [System.IO.File]::WriteAllText($dest, $text, (New-Object System.Text.UTF8Encoding($false)))
    Write-Output ($rel + ' :: ' + $enc.WebName)
}
