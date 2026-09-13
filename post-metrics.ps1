param([string]$Suffix = "M01")

$base = "http://localhost:8080/api/v1"
$acct = "ACC-MET-$Suffix"
$now  = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

# Credentials live in login.json, which is gitignored. Nothing secret in this file.
if (-not (Test-Path "$PWD\login.json")) { throw "login.json missing" }
$loginRaw = curl.exe -s -X POST "$base/auth/login" `
    -H "Content-Type: application/json" --data-binary "@login.json"
$token = ($loginRaw | ConvertFrom-Json).token
if (-not $token) { throw "no token in login response: $loginRaw" }
Write-Host "token acquired ($($token.Length) chars)"

# Three small IE transactions, then one large IR: the fourth trips all three
# rules at once (amount > 10000, high-risk country, velocity > limit).
$cases = @(
    @{ ref = "TX-$Suffix-1"; amount = "100.00";   country = "IE" },
    @{ ref = "TX-$Suffix-2"; amount = "100.00";   country = "IE" },
    @{ ref = "TX-$Suffix-3"; amount = "100.00";   country = "IE" },
    @{ ref = "TX-$Suffix-4"; amount = "20000.00"; country = "IR" }
)

foreach ($c in $cases) {
    $json = @"
{
  "transactionRef": "$($c.ref)",
  "accountId": "$acct",
  "amount": $($c.amount),
  "currency": "EUR",
  "destinationCountry": "$($c.country)",
  "transactionType": "TRANSFER",
  "occurredAt": "$now"
}
"@
    # WriteAllText with no BOM: ConvertTo-Json mangles quotes for native exes,
    # and a BOM makes Jackson fail parsing with a 400.
    [IO.File]::WriteAllText("$PWD\body.json", $json, (New-Object Text.UTF8Encoding $false))

    # curl.exe, not curl: in PowerShell "curl" is an alias for Invoke-WebRequest.
    $code = curl.exe -s -o NUL -w "%{http_code}" -X POST "$base/transactions" `
        -H "Content-Type: application/json" `
        -H "Authorization: Bearer $token" `
        --data-binary "@body.json"

    Write-Host "$($c.ref)  ->  $code"
}
