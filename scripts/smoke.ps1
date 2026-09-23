param([string]$BaseUrl = 'http://localhost:18080', [int]$Iterations = 100000, [int]$BatchSize = 10000, [long]$Seed = 42)
$ErrorActionPreference = 'Stop'
$body = @{
  players = @(@{name='AA';cards=@('AS','AH')},@{name='KK';cards=@('KS','KH')},@{name='QQ';cards=@('QS','QH')})
  board = @(); iterations = $Iterations; batchSize = $BatchSize; seed = $Seed
} | ConvertTo-Json -Depth 5
$created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/simulations" -ContentType 'application/json' -Body $body
$deadline = (Get-Date).AddMinutes(5)
do {
  Start-Sleep -Seconds 1
  $status = Invoke-RestMethod -Uri "$BaseUrl$($created.statusUrl)"
  if ($status.status -in @('FAILED','CANCELLED')) { throw "Simulation ended: $($status.status)" }
  if ((Get-Date) -gt $deadline) { throw 'Simulation timed out' }
} while ($status.status -ne 'COMPLETED')
$result = Invoke-RestMethod -Uri "$BaseUrl$($created.statusUrl)/results"
if ($result.totalTrials -ne $Iterations) { throw 'Trial count mismatch' }
$equity = ($result.players | Measure-Object -Property equity -Sum).Sum
if ([Math]::Abs($equity - 1) -gt 0.00000001) { throw 'Equity does not conserve pots' }
$result | ConvertTo-Json -Depth 5
