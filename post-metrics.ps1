param([string]$Suffix = "M01")

$acct = "ACC-MET-$Suffix"
$url  = "http://localhost:8080/api/v1/transactions"
$now  = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

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
    $code = curl.exe -s -o NUL -w "%{http_code}" -X POST $url `
        -H "Content-Type: application/json" --data-binary "@body.json"

    Write-Host "$($c.ref)  ->  $code"
}