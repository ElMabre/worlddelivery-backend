# Clúster RabbitMQ de dos nodos

Imagen oficial fijada a `rabbitmq:4.2.9-management`, dos nombres estables y un volumen por nodo. El archivo `rabbitmq.conf` usa descubrimiento estático; no consulta AWS. La cookie común y las credenciales se reciben por variables de entorno.

## Arranque del clúster

Necesitas Docker Engine/Compose con contenedores Linux. Desde esta carpeta:

```powershell
Copy-Item .env.example .env
# Edita .env: reemplaza usuario, contraseña y cookie por valores propios.
# Una cookie aleatoria puede generarse localmente con:
# [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
docker compose config --quiet
docker compose up -d --wait
docker compose ps
.\Verify-Cluster.ps1
```

`config --quiet` valida sin imprimir los valores interpolados.

El healthcheck `ping` comprueba la VM Erlang para que el segundo nodo pueda iniciar durante una recuperación conjunta. **No certifica que el clúster esté listo.** `Verify-Cluster.ps1` comprueba que RabbitMQ esté arrancado y que cada nodo vea los dos miembros activos. Si falla, espera y vuelve a ejecutarlo; revisa `docker compose logs --tail=100` si persiste. No declares las colas antes de esta verificación.

En Linux, la comprobación equivalente es:

```bash
docker compose exec -T rabbitmq-1 rabbitmq-diagnostics -q check_running
docker compose exec -T rabbitmq-2 rabbitmq-diagnostics -q check_running
docker compose exec -T rabbitmq-1 rabbitmqctl cluster_status
docker compose exec -T rabbitmq-2 rabbitmqctl cluster_status
```

Ambas salidas deben mostrar `rabbit@rabbitmq-1` y `rabbit@rabbitmq-2` como running nodes. Si se formaron dos clústeres aislados, no continúes: inspecciona logs, cookie y conectividad. No se incluye un reset automático porque destruiría estado.

## Declarar y observar la topología

Con ambos nodos activos, exporta `RABBITMQ_USERNAME` y `RABBITMQ_PASSWORD` para el proceso Java usando los mismos valores que configuraste en .env. Compose carga su .env; Spring Boot no lo hace automáticamente.

Desde la raíz del backend:

```powershell
.\mvnw.cmd -pl ms-operaciones spring-boot:run
```

El servicio declara 3 exchanges, 6 colas y 6 bindings al arrancar. Sus tres consumidores escuchan las colas principales y confirman cada mensaje después de guardar el resultado. Después puedes iniciar ms-pedidos para publicar sus eventos pendientes.

Desde la carpeta de Compose:

```bash
docker compose exec -T rabbitmq-1 rabbitmqctl list_exchanges name type durable
docker compose exec -T rabbitmq-1 rabbitmqctl list_queues name type durable arguments
docker compose exec -T rabbitmq-1 rabbitmqctl list_bindings source_name destination_name routing_key
# Repetir quorum_status para cada una de las seis colas de la tabla:
docker compose exec -T rabbitmq-1 rabbitmq-queues quorum_status --vhost / q.despacho.tickets
```

| Cola principal | Routing key / patrón | DLQ |
|---|---|---|
| q.despacho.tickets | pedido.creado | q.despacho.tickets.dlq |
| q.notificaciones.clientes | pedido.estado.* | q.notificaciones.clientes.dlq |
| q.auditoria.eventos | pedido.# | q.auditoria.eventos.dlq |

Comprueba que **cada quorum_status muestra dos miembros**. Si una cola se creó antes de estar ambos nodos activos, su grupo inicial podría tener un único miembro: el parámetro inicial no amplía automáticamente una cola ya existente. En ese caso, no la borres ni la recrees con mensajes; revisa su membresía antes de seguir.

Exchanges esperados: `wd.pedidos.direct` direct, `wd.eventos.topic` topic y `wd.dlx.direct` direct. El broker también muestra sus exchanges y bindings predeterminados: no se incluyen en las cantidades del proyecto.

Las colas principales tienen `x-dead-letter-exchange`, una clave DLQ por dominio, `x-dead-letter-strategy=at-least-once` y `x-overflow=reject-publish`. Las DLQ no tienen TTL ni otro DLX: retienen los fallos para inspección. Todas son quorum durables, con grupo inicial de dos miembros y límite de 10000 mensajes. Al alcanzar el límite, se rechazan publicaciones; los pendientes permanecen en el productor.

## Acceso y persistencia

- AMQP: localhost:5672 y localhost:5673.
- Management: http://localhost:15672 y http://localhost:15673.
- Los puertos se publican solo en loopback; en EC2 los procesos Java del mismo host pueden usarlos. Para acceso remoto a Management, utiliza un túnel SSH cuando se prepare el despliegue.
- Los contenedores se comunican por la red Docker, incluyendo puertos internos de clustering; no se publican esos puertos en el host.
- Reinicio: `docker compose restart`; después repetir verificación de clúster y membresía.
- Parada conservando datos: `docker compose down` (sin `-v`). Conservar la misma cookie y los volúmenes.
- Cambiar variables del usuario inicial no cambia automáticamente un usuario de un volumen ya inicializado.

**Límite de dos nodos:** una cola con dos réplicas necesita ambas disponibles para conservar mayoría. Este diseño cumple el ejercicio de dos nodos, pero no tolera la caída de uno. Para tolerar un fallo se necesitan al menos tres miembros. Dos contenedores en una EC2 tampoco protegen de la caída de ese host.
