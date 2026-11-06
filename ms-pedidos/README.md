# ms-pedidos

API de pedidos en el puerto 8080. Contrato de la API utilizada por Angular:

| Método | Ruta | Scope | Respuesta |
|---|---|---|---|
| GET | /api/pedidos | api-access | 200, lista ordenada por ID |
| POST | /api/pedidos | write | 201, pedido creado |
| PATCH | /api/pedidos/{id}/estado | update | 200, pedido actualizado |
| DELETE | /api/pedidos/{id} | delete | 204, desactivación lógica; 404 si no está activo |
| PUT | /api/pedidos/{id} | update | 200, reemplazo de los campos editables |
| GET | /api/pedidos/inactivos | admin y grupo Admin | 200, pedidos desactivados |

Todos los scopes tienen el prefijo `resource-server-worlddelivery/`. Se conserva la validación JWT del Resource Server.

## Persistencia y validación

`PedidoController` recibe DTOs; `PedidoService` aplica transacciones; `PedidoRepository` utiliza JPA sobre PostgreSQL. Los IDs los genera la base de datos y `@Version` detecta modificaciones concurrentes (409).

Producto, cliente y dirección son obligatorios, no pueden estar en blanco y tienen límites de 200, 150 y 300 caracteres. La fecha debe ser una fecha ISO válida y el estado uno de: PENDIENTE, EN PREPARACION, EN TRANSITO, EN REPARTO o ENTREGADO. Se conservan fechas históricas, necesarias para pedidos ya entregados. El cuerpo JSON continúa usando estos estados con espacios.

Los errores de entrada devuelven 400 con `detail` y, en errores de validación de campos, el objeto `errores`. Un pedido inexistente devuelve 404.

Con `SEED_ENABLED=true`, los 28 pedidos iniciales se cargan solo si la tabla está vacía. El seed no publica eventos: son datos históricos de demostración. Por defecto está desactivado. Si se eliminan todos los pedidos y se reinicia con el seed activado, volverán a cargarse.

## Eventos y consistencia

Cada operación guarda el cambio y sus eventos pendientes dentro de la misma transacción de base de datos. Si hay rollback, se revierten ambos. La API confirma la persistencia del pedido; el trabajo asíncrono puede estar pendiente.

Un publicador consulta hasta 50 eventos cada 2 segundos. Envía JSON persistente con `messageId`, espera confirmación positiva del broker y comprueba que no haya una devolución por falta de ruta. Solo entonces elimina la entrega pendiente. Si el broker falla, conserva el registro y vuelve a intentarlo en el siguiente ciclo; no guarda datos personales en el log.

| Operación | Exchange | Routing key |
|---|---|---|
| Crear | wd.pedidos.direct | pedido.creado |
| Crear (auditoría) | wd.eventos.topic | pedido.creado |
| Cambiar estado | wd.eventos.topic | pedido.estado.en_reparto (según estado) |
| Editar | wd.eventos.topic | pedido.modificado |
| Eliminar | wd.eventos.topic | pedido.eliminado |

Los nombres se definen en `application.yml`, enlazados por `MensajeriaProperties` desde `MensajeriaConfig`. La lógica de negocio no conoce nombres de exchanges ni colas. La topología se declara desde ms-operaciones, después de verificar los dos nodos del clúster. Mientras no exista, las publicaciones quedan pendientes.

Contrato JSON: `eventId` (UUID), `tipo` (CREADO, ESTADO_ACTUALIZADO, MODIFICADO o ELIMINADO), `ocurridoEn` (Instant) y `pedido` (snapshot con id, producto, cliente, direccion, fechaEntrega y estado). Las dos entregas de creación comparten eventId. Cada operación posterior tiene un ID nuevo.

La entrega es **al menos una vez**: un reinicio después del ACK del broker y antes de borrar el registro puede duplicar el evento. Los consumidores identifican duplicados por eventId y dominio. El publicador está diseñado para **una instancia de ms-pedidos**; ejecutar varias requiere coordinación/locking adicional. Si la primera entrega falla, se detiene el lote para no adelantar las posteriores.

Los reintentos de publicación del outbox son independientes de los intentos de consumo en ms-operaciones.

## Configuración y ejecución

Variables en `.env.example`, sin secretos reales. `.env` está ignorado y **no se carga automáticamente**: exportar las variables o configurarlas en el proceso del servicio.

```powershell
# Desde la raíz del repositorio, después de configurar las variables:
.\mvnw.cmd -pl ms-pedidos spring-boot:run
```

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: PostgreSQL local o RDS. En RDS, usar TLS con verificación de nombre y CA.
- `COGNITO_ISSUER_URI`: emisor autorizado. El arranque real requiere su configuración; los tests lo sustituyen.
- `RABBITMQ_ADDRESSES` (por defecto localhost:5672,localhost:5673), `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`: conexión a los nodos del broker. Los antiguos RABBITMQ_HOST/PORT se aceptan como primer destino cuando no se define RABBITMQ_ADDRESSES.
- `CORS_ALLOWED_ORIGINS`: orígenes separados por comas. Vacío por defecto. Permite GET, POST, PUT, PATCH, DELETE y OPTIONS, con Authorization y Content-Type. El preflight de Gateway debe admitir el mismo origen.
- `DB_DDL_AUTO=update`: crea/actualiza tablas para el proyecto académico. Después de inicializar la base, usar validate en los siguientes arranques.
- `OUTBOX_ENABLED=false`: desactiva el envío programado, pero mantiene el registro de eventos.

## Edición y borrado lógico

La lista habitual incluye solo pedidos activos. DELETE marca `activo=false` y registra el evento ELIMINADO en la misma transacción. Los pedidos inactivos quedan fuera de las modificaciones y del endpoint habitual. `GET /inactivos` requiere access token, grupo Cognito Admin y scope admin.

PUT recibe producto, cliente, direccion, fechaEntrega y estado con las mismas validaciones de creación. Si el estado persistido y el estado solicitado son ENTREGADO, cambiar cualquiera de los cuatro campos de datos devuelve 409. Cambiar a otro estado en la misma petición permite editar todos los datos.

Toda edición completa registra MODIFICADO con routing key `pedido.modificado` en `wd.eventos.topic`. Si el estado también cambia, registra además ESTADO_ACTUALIZADO para notificaciones. Cada evento tiene su propio eventId. El snapshot de eventos mantiene su formato anterior para que mensajes pendientes y archivos de deduplicación sigan siendo compatibles.

Antes de arrancar esta versión sobre una base existente, ejecutar `infra/deploy/sql/001-pedidos-activo.sql`. Los registros existentes reciben `activo=true`. Mantener `DB_DDL_AUTO=validate` después de la migración.
