param([Security.SecureString]$AccessToken)
$ErrorActionPreference='Stop'
# Ejecutar manualmente: API local, recursos exclusivos de esta prueba.
if($null -eq $AccessToken) { $AccessToken=Read-Host 'Access token Cognito con scope admin' -AsSecureString }
$credential=New-Object System.Management.Automation.PSCredential('token',$AccessToken)
$headers=@{Authorization='Bearer '+$credential.GetNetworkCredential().Password}
$base='http://127.0.0.1:8082/api/rabbitmq'
$suffix=[guid]::NewGuid().ToString('N')
$queue='demo.admin.q.'+$suffix
$exchange='demo.admin.x.'+$suffix
try {
    $body=@{name=$exchange;type='TOPIC'} | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post -Uri "$base/exchanges" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
    $body=@{name=$queue} | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post -Uri "$base/queues" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
    $body=@{queue=$queue;exchange=$exchange;routingKey='demo.#'} | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post -Uri "$base/bindings" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
    $state=Invoke-RestMethod -Method Get -Uri "$base/queues/$queue" -Headers $headers
    if($state.name -ne $queue -or $state.messagesReady -ne 0 -or $state.consumers -ne 0) { throw 'Estado de cola inesperado' }
    $key=[Uri]::EscapeDataString('demo.#')
    Invoke-RestMethod -Method Delete -Uri "$base/bindings?queue=$queue&exchange=$exchange&routingKey=$key" -Headers $headers | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$base/queues/$queue" -Headers $headers | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$base/exchanges/$exchange" -Headers $headers | Out-Null
    Write-Host 'OK: creacion, consulta y eliminacion de recursos de prueba completadas.'
} catch {
    Write-Host "Prueba incompleta. Revisar recursos $queue y $exchange antes de repetir."
    throw
} finally {
    $headers.Clear()
    $credential=$null
    $AccessToken=$null
}
