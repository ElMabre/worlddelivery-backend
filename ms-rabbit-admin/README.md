# ms-rabbit-admin

Spring Boot 4.1.1, Java 17, puerto 8082. El controlador delega todas las operaciones AMQP en `RabbitAdminService`; `RabbitAdmin` se configura en un bean independiente sin declaración automática ni recreación de recursos borrados.

## Contrato REST

Todas las rutas requieren `Authorization: Bearer <access_token>` y scope `resource-server-worlddelivery/admin` (configurable mediante `RABBIT_ADMIN_SCOPE`).

| Método y ruta | Entrada | Respuesta correcta |
|---|---|---|
| POST /api/rabbitmq/queues | `{"name":"demo.queue"}` | 201, nombre |
| GET /api/rabbitmq/queues/{name} | Nombre en ruta | 200, name/messagesReady/consumers |
| DELETE /api/rabbitmq/queues/{name} | Nombre en ruta | 204 |
| POST /api/rabbitmq/exchanges | `{"name":"demo.exchange","type":"TOPIC"}` | 201, nombre y tipo |
| DELETE /api/rabbitmq/exchanges/{name} | Nombre en ruta | 204 |
| POST /api/rabbitmq/bindings | `{"queue":"demo.queue","exchange":"demo.exchange","routingKey":"demo.#"}` | 201, binding |
| DELETE /api/rabbitmq/bindings | Query parameters queue, exchange, routingKey | 204 |

Codificar `#` como `%23` en query parameters. No enviar cuerpo en DELETE bindings. Tipos de exchange: DIRECT, TOPIC y FANOUT; son durables y no auto-delete. Las nuevas colas son quorum durables, no exclusivas, con dos réplicas iniciales, máximo 10000 mensajes y reject-publish; estos valores están centralizados en YAML. Las colas creadas mediante esta API son recursos administrativos independientes: no incorporan automáticamente la DLX de los consumidores. La topología de negocio y sus DLQ siguen a cargo de ms-operaciones; no redeclararlas con esta API, porque sus argumentos son diferentes.

Nombres: 1 a 128 caracteres ASCII alfanuméricos, punto, guion y guion bajo; comienzan con alfanumérico y no con `amq.`. Routing key: hasta 128 caracteres, incluyendo `*` y `#`; puede estar vacía para fanout/direct. Los DTO, query parameters y path variables se validan. Se rechazan propiedades desconocidas para no ignorar una configuración enviada por error.

400: entrada inválida; 401: JWT ausente/inválido/vencido; 403: sin scope; 404: el broker informa recurso ausente; 409: el broker informa precondición/configuración incompatible; 503: fallo o respuesta no concluyente del broker. RabbitAdmin puede devolver null/false al fallar algunas operaciones: la API no convierte esa situación en un éxito ni en un 404 ficticio. Si se pierde la respuesta, la operación pudo haberse aplicado: verificar antes de repetir.

GET consulta una cola por nombre, no enumera todas las colas/exchanges/bindings del broker. `messagesReady` no incluye mensajes entregados sin ACK. No se mantiene un listado ficticio en memoria. DELETE cola elimina también sus mensajes y bindings; DELETE exchange elimina sus bindings. Para la demo usar exclusivamente los recursos con nombres únicos que crea el script. El frontend deberá pedir confirmación explícita de borrado.

## Seguridad y Cognito

El decoder verifica firma RS256 con JWKS, issuer, vigencia, existencia de exp, `token_use=access` y `client_id=COGNITO_CLIENT_ID`. El scope lo comprueba Spring Security en todas las rutas del administrador. El JWKS se obtiene al validar tokens, sin discovery en el arranque. No se acepta un ID token como sustituto. Referencia: [access tokens de Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-the-access-token.html).

Se eligió scope `admin` separado. Reutilizar `delete` simplificaría la configuración, pero concedería administración de infraestructura a quien solo puede borrar pedidos. El scope nuevo permite conceder y revocar ambos permisos de manera independiente.

Pendiente en tu cuenta, sin acciones ejecutadas por el agente: agregar el custom scope admin al resource server, habilitarlo en el app client autorizado y solicitarlo en el flujo OAuth. No reemplazar los scopes existentes. `aws.cognito.signin.user.admin` NO sustituye al scope de esta API. La habilitación de un scope en un app client no distingue usuarios administradores: si el cliente es compartido, la emisión de admin debe restringirse por política/grupo en Cognito o añadirse un control de grupo al backend antes de uso real. La configuración de esa concesión se resolverá en la etapa de despliegue.

API Gateway deberá proteger `/api/rabbitmq/{proxy+}` con el scope admin y reenviar Authorization; el backend aplica su propia verificación. CORS se configura con `CORS_ALLOWED_ORIGINS`, sin origen fijo ni wildcard por defecto. En esta API se admiten GET, POST, DELETE y OPTIONS; PATCH corresponde a pedidos.

## Arranque y prueba manual

Desde la raíz del backend, exportar variables de `.env.example` en una terminal exclusiva para este servicio. `.env` no se carga automáticamente. RABBITMQ_USERNAME y RABBITMQ_PASSWORD deben existir en tu entorno; no escribirlos en archivos versionados.

```powershell
$env:SERVER_PORT='8082'
$env:COGNITO_ISSUER_URI='https://cognito-idp.REGION.amazonaws.com/USER_POOL_ID'
$env:COGNITO_CLIENT_ID='TU_APP_CLIENT_ID'
$env:RABBIT_ADMIN_SCOPE='resource-server-worlddelivery/admin'
$env:CORS_ALLOWED_ORIGINS='http://localhost:4200'
.\mvnw.cmd -pl ms-rabbit-admin spring-boot:run
```

Sustituir los marcadores públicos de issuer/client ID antes de arrancar. Los dos nodos RabbitMQ deben estar activos para las colas quorum. El arranque de este servicio no acredita conexión al broker: se conecta al ejecutar una operación.

En otra terminal desde la raíz del backend:

```powershell
curl.exe -i http://localhost:8082/api/rabbitmq/queues/demo
# Esperado: 401.
.\ms-rabbit-admin\demo\Test-AdminApi.ps1
```

El script pide un access token sin mostrarlo; crea una cola y un exchange con UUID, crea el binding, consulta los conteos y elimina esos tres recursos. Esperado: mensaje OK. Con un token válido que solo tenga el scope delete, debe fallar con 403 antes de crear recursos. No compartir el token ni guardarlo en el repositorio. El script no publica mensajes ni modifica las seis colas de negocio.

## Verificación

Resultado de esta etapa: `mvn test` con 62 pruebas aprobadas (22 pedidos, 25 operaciones, 15 administrador), `ng build` correcto y 4 pruebas Angular aprobadas. Ejecución sobre JVM 21.0.11 con `release 17`; ejecución sobre JVM 17 todavía pendiente.

Las pruebas levantan el contexto y los filtros reales, sustituyen JwtDecoder y RabbitAdmin para no contactar Cognito ni RabbitMQ, y validan por separado los claims del JWT. Cubren las seis mutaciones, permisos, DTO/path/query, propiedades de colas, tipos de exchange, bindings, CORS y errores del broker. La firma real y las operaciones contra el clúster quedan para la prueba manual; no se afirma una ejecución real de estas pruebas de integración.

Referencia del adaptador usado: [RabbitAdmin, Spring AMQP](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/rabbit/core/RabbitAdmin.html).

## Defensa en cinco líneas

1. Separé el administrador en su propio servicio y puerto para independizarlo de pedidos y consumidores.
2. Encapsulé RabbitAdmin en un servicio, dejando el controlador centrado en el contrato HTTP.
3. Validé nombres, tipos y bindings para rechazar solicitudes inválidas antes de llegar al broker.
4. Exigí access token y scope admin separado para no confundir borrar pedidos con administrar RabbitMQ.
5. Probé permisos, operaciones y errores sin AWS, y preparé una demo local para la integración real.
