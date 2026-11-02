# ms-operaciones

Spring Boot 4.1.1, compilación Java 17, puerto 8081. Tres consumidores independientes de despacho, notificaciones y auditoría; ACK manual, prefetch 1, un consumidor por dominio.

## Arranque y demo local

Primero levantar y verificar [el clúster](../infra/rabbitmq-cluster/README.md). Mantener ambos nodos encendidos. Si el servicio anterior sigue abierto, detenerlo con Ctrl+C en su propia terminal y arrancar esta versión. Desde la raíz del backend, en la terminal que ya tiene RABBITMQ_USERNAME y RABBITMQ_PASSWORD:

```powershell
$env:DEMO_FAILURE_ENABLED='true'
$env:OPERACIONES_STORAGE_DIR=Join-Path (Get-Location) 'ms-operaciones/data/operaciones'
.\mvnw.cmd -pl ms-operaciones spring-boot:run
```

No se carga `.env` automáticamente; las variables requeridas están en `.env.example`. Si abres una terminal nueva, exporta nuevamente las variables locales. No necesitas credenciales de AWS. En otra terminal desde la raíz del backend:

```powershell
$credencialRabbit=Get-Credential -Message 'Usuario y clave de RabbitMQ local'
.\ms-operaciones\demo\Publish-Demo.ps1 -Dominio despacho -Caso ok -Credential $credencialRabbit
.\ms-operaciones\demo\Publish-Demo.ps1 -Dominio despacho -Caso dlq -Credential $credencialRabbit
```

El primer evento genera un archivo en `ms-operaciones/data/operaciones/despacho/` y un log `ACK_ENVIADO`. El segundo muestra intentos 1, 2 y 3, dos `REINTENTO` y `NACK_DLQ_SOLICITADO`; no genera resultado de negocio. Desde la carpeta del clúster comprobar:

```powershell
docker compose exec -T rabbitmq-1 rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
```

Esperado una vez estabilizado: un consumidor en cada cola principal y cero en cada DLQ; `q.despacho.tickets.dlq` incrementa su número de mensajes listos en uno por cada demo fallida. Las colas principales deberían quedar sin mensajes pendientes. Registrar los valores antes y después; no se purgan datos automáticamente. Para comprobar el contenido, abrir RabbitMQ Management en localhost:15672, la cola DLQ y Get messages con requeue activado; buscar el eventId mostrado por el script y la marca `[ERROR-DLQ]`.

Repetir para otros dominios:

```powershell
.\ms-operaciones\demo\Publish-Demo.ps1 -Dominio notificaciones -Caso ok -Credential $credencialRabbit
.\ms-operaciones\demo\Publish-Demo.ps1 -Dominio notificaciones -Caso dlq -Credential $credencialRabbit
.\ms-operaciones\demo\Publish-Demo.ps1 -Dominio auditoria -Caso ok -Credential $credencialRabbit
.\ms-operaciones\demo\Publish-Demo.ps1 -Dominio auditoria -Caso dlq -Credential $credencialRabbit
.\ms-operaciones\demo\Publish-Demo.ps1 -Dominio despacho -Caso invalid -Credential $credencialRabbit
Remove-Variable credencialRabbit
```

La demo de notificaciones también llega a auditoría por `pedido.#`: en el caso fallido aumentan ambas DLQ. El JSON inválido se rechaza inmediatamente, sin reintentos de negocio. El script publica únicamente en localhost mediante la [API HTTP oficial de RabbitMQ](https://www.rabbitmq.com/docs/http-api-reference#put--api-exchanges-vhost-name-publish); `routed=true` acredita una ruta, no el ACK ni la llegada a DLQ. El productor real usa AMQP y outbox: esta demo aislada no prueba el recorrido JPA → outbox → consumidores.

## Procesamiento y resultados

`application.yml` y `TopologiaProperties` centralizan exchanges, colas y bindings. Los listeners resuelven sus nombres desde propiedades. `RabbitTopologyConfig` declara los recursos y conserva DLX, quorum y dead lettering at-least-once de la etapa anterior.

`ProcesadorMensaje` valida JSON, DTO y tipo de evento. Un fallo recuperable ejecuta como máximo tres intentos dentro de la misma entrega, sin devolverla entre intentos al broker. El retry automático de Spring está desactivado para no multiplicar reintentos. Al agotarse, ejecuta `basicNack(tag,false,false)`; RabbitMQ se encarga de la DLQ. Errores permanentes y mensajes inválidos se rechazan directamente. Los logs registran dominio, eventId, intento y causa, sin el cuerpo ni credenciales.

Cada servicio guarda el snapshot del evento en su carpeta: despacho representa un ticket; notificaciones es una simulación local, sin envío de correo; auditoría conserva el registro. El resultado se escribe antes del ACK mediante un archivo temporal y movimiento atómico. Cada eventId se guarda una vez por dominio y los duplicados idénticos se omiten incluso al reconstruir el registro. El mismo ID con otro contenido se rechaza. Si falla el ACK, el error de canal se propaga y no se ejecuta un NACK adicional.

Alcance: una instancia de ms-operaciones, un consumidor por dominio y disco local persistente que soporte movimiento atómico. No es una solución distribuida de deduplicación ni garantiza supervivencia ante pérdida del disco o apagado físico durante escritura. No borrar la carpeta mientras haya posibles redeliveries. Para múltiples instancias se necesitará un registro transaccional compartido. Fuera de la demo, establecer `DEMO_FAILURE_ENABLED=false`. La carpeta de datos está ignorada por Git.

## Verificación y límites

`mvn test` desde la raíz: topología, validación, tres listeners manuales, éxito, dos reintentos, agotamiento, fallo permanente, JSON inválido, demo desactivada, errores ACK/NACK, resultados por dominio y duplicados. Los tests usan un Channel simulado y archivos temporales; no ejecutan AWS ni Docker. El contrato JSON incluye fechas ISO y estados con espacios.

Verificación local de esta etapa: 47 pruebas backend (22 pedidos, 10 topología, 15 consumidores), `ng build` y 4 pruebas Angular aprobadas; JARs empaquetados después de las pruebas. Ejecutado con JVM 21.0.11 y compilación `release 17`; no se ha ejecutado aún sobre una JVM 17. En el entorno restringido se configuró `java.io.tmpdir` dentro de la carpeta de trabajo para permitir movimientos atómicos en las pruebas.

La salida aportada por el estudiante el 2 de noviembre confirma dos nodos activos y seis colas quorum con ambos miembros, líder/seguidor y Commit Index/Last Applied sincronizados. La salida posterior del estudiante confirma tres consumidores activos, colas principales vacías sin mensajes sin ACK, cuatro archivos de resultados, ACK en casos correctos, dos reintentos por fallo recuperable y rechazos sin reencolar. Las DLQ presentan despacho=2 (fallo y JSON inválido), notificaciones=1 y auditoría=2, coherentes con la demo. Las tres colas principales muestran ambos nodos sincronizados. No se ha acreditado persistencia tras reiniciar ni el recorrido completo desde PostgreSQL.

## Defensa en cinco líneas

1. Separé tres listeners y servicios para que cada dominio procese sus propios eventos.
2. Confirmo manualmente después de guardar el resultado y reconozco duplicados por eventId.
3. Reintento dos veces los fallos recuperables y rechazo sin reencolar al agotarlos.
4. La marca de demo provoca el fallo controlado y RabbitMQ deriva el mensaje a su DLQ.
5. Probé los caminos de éxito y error; la notificación es simulada y la prueba real se documenta aparte.
