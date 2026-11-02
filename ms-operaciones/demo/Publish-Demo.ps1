param(
    [ValidateSet('despacho','notificaciones','auditoria')][string]$Dominio='despacho',
    [ValidateSet('ok','dlq','invalid')][string]$Caso='ok',
    [System.Management.Automation.PSCredential]$Credential
)
$ErrorActionPreference='Stop'
# Exclusivamente demo local. El productor del servicio usa AMQP y outbox.
$routes=@{
    despacho=@{Exchange='wd.pedidos.direct'; Key='pedido.creado'; Tipo='CREADO'}
    notificaciones=@{Exchange='wd.eventos.topic'; Key='pedido.estado.en_transito'; Tipo='ESTADO_ACTUALIZADO'}
    auditoria=@{Exchange='wd.eventos.topic'; Key='pedido.eliminado'; Tipo='ELIMINADO'}
}
$route=$routes[$Dominio]
$eventId=[guid]::NewGuid().ToString()
$producto='Producto demo'
if($Caso -eq 'dlq') { $producto+=' [ERROR-DLQ]' }
$estado='PENDIENTE'
if($Dominio -eq 'notificaciones') { $estado='EN TRANSITO' }
$evento=@{
    eventId=$eventId; tipo=$route.Tipo; ocurridoEn=[DateTime]::UtcNow.ToString('o')
    pedido=@{id=1; producto=$producto; cliente='Cliente demo'; direccion='Direccion demo';
        fechaEntrega=[DateTime]::Today.AddDays(1).ToString('yyyy-MM-dd'); estado=$estado}
}
$payload=$evento | ConvertTo-Json -Depth 8 -Compress
if($Caso -eq 'invalid') { $payload='{"incompleto":' }
$body=@{
    properties=@{content_type='application/json'; delivery_mode=2; message_id=$eventId}
    routing_key=$route.Key; payload=$payload; payload_encoding='string'
} | ConvertTo-Json -Depth 10 -Compress
if($null -eq $Credential) { $Credential=Get-Credential -Message 'Usuario y clave de RabbitMQ local' }
if($null -eq $Credential) { throw 'No se proporcionaron credenciales locales.' }
$authorization=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    $Credential.UserName+':'+$Credential.GetNetworkCredential().Password))
try {
    $uri='http://127.0.0.1:15672/api/exchanges/%2F/'+[Uri]::EscapeDataString($route.Exchange)+'/publish'
    $response=Invoke-RestMethod -Method Put -Uri $uri -Headers @{Authorization="Basic $authorization"} `
        -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    if(-not $response.routed) { throw 'Mensaje sin ruta: revisar exchanges y bindings.' }
    Write-Host "Publicado eventId=$eventId dominio=$Dominio caso=$Caso. Revisar logs y colas; routed no acredita el consumo."
} finally {
    $authorization=$null
}
