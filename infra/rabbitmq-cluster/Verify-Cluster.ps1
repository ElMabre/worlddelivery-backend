[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Configuración Compose inválida o variables ausentes.' }
    $expected = @('rabbit@rabbitmq-1', 'rabbit@rabbitmq-2')
    foreach ($node in @('rabbitmq-1', 'rabbitmq-2')) {
        & docker compose exec -T $node rabbitmq-diagnostics -q check_running
        if ($LASTEXITCODE -ne 0) { throw "La aplicación RabbitMQ no está lista en $node. Espera y vuelve a verificar." }
        $raw = & docker compose exec -T $node rabbitmqctl cluster_status --formatter json
        if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar el clúster desde $node." }
        $status = ($raw -join "`n") | ConvertFrom-Json
        $actual = @($status.running_nodes)
        if ($actual.Count -ne 2 -or (Compare-Object $expected $actual)) {
            throw "No hay exactamente dos nodos en el mismo clúster vistos desde $node. No arranques ms-operaciones todavía."
        }
        Write-Output "$node confirma ambos nodos activos."
    }
    Write-Output 'Clúster verificado. Puedes arrancar ms-operaciones y revisar la topología.'
} finally {
    Pop-Location
}
