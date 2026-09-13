# Produces a spread of APPROVE / REVIEW / BLOCK for screenshots.
# Account ids are deliberately varied: velocity counts per account, so reusing
# one account would make unrelated transactions trip the velocity rule.
param([string]$Suffix = "DEMO")

$base = "http://localhost:8080/api/v1"

# Backdated 5 minutes. occurredAt is @PastOrPresent and the timestamp is taken
# on the client: "now" is already in the future by the time the server checks.
$now  = (Get-Date).ToUniversalTime().AddMinutes(-5).ToString("yyyy-MM-ddTHH:mm:ssZ")

if (-not (Test-Path "$PWD\login.json")) { throw "login.json missing" }
$token = (curl.exe -s -X POST "$base/auth/login" `
    -H "Content-Type: application/json" --data-binary "@login.json" | ConvertFrom-Json).token
if (-not $token) { throw "login failed" }

$cases = @(
# Ordinary traffic: under the 10000 threshold, low-risk country.
    @{ ref = "TX-$Suffix-01"; acct = "ACC-100234"; amount = "45.90";    country = "IE" },
    @{ ref = "TX-$Suffix-02"; acct = "ACC-100871"; amount = "1250.00";  country = "DE" },
    @{ ref = "TX-$Suffix-03"; acct = "ACC-100512"; amount = "89.99";    country = "FR" },
    @{ ref = "TX-$Suffix-04"; acct = "ACC-100234"; amount = "320.00";   country = "ES" },

    # Exactly one rule each -> 40 -> REVIEW.
    @{ ref = "TX-$Suffix-05"; acct = "ACC-100655"; amount = "18500.00"; country = "IE" },
    @{ ref = "TX-$Suffix-06"; acct = "ACC-100903"; amount = "75.00";    country = "IR" },

    # Same account four times in the window: the 4th trips velocity, and amount
    # plus country make it three hits at once -> 120, clamped to 100 -> BLOCK.
    @{ ref = "TX-$Suffix-07"; acct = "ACC-100777"; amount = "200.00";   country = "IE" },
    @{ ref = "TX-$Suffix-08"; acct = "ACC-100777"; amount = "150.00";   country = "IE" },
    @{ ref = "TX-$Suffix-09"; acct = "ACC-100777"; amount = "410.00";   country = "IE" },
    @{ ref = "TX-$Suffix-10"; acct = "ACC-100777"; amount = "42000.00"; country = "IR" }
)

foreach ($c in $cases) {
    $json = @"
{
  "transactionRef": "$($c.ref)",
  "accountId": "$($c.acct)",
  "amount": $($c.amount),
  "currency": "EUR",
  "destinationCountry": "$($c.country)",
  "transactionType": "TRANSFER",
  "occurredAt": "$now"
}
"@
    [IO.File]::WriteAllText("$PWD\body.json", $json, (New-Object Text.UTF8Encoding $false))

    $code = curl.exe -s -o NUL -w "%{http_code}" -X POST "$base/transactions" `
        -H "Content-Type: application/json" `
        -H "Authorization: Bearer $token" `
        --data-binary "@body.json"

    Write-Host "$($c.ref)  $($c.acct.PadRight(12))  ->  $code"
}
