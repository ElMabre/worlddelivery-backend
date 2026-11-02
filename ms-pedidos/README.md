# ms-pedidos

API de pedidos en el puerto 8080. Conserva el contrato que utiliza Angular:

| Método | Ruta | Scope | Respuesta |
|---|---|---|---|
| GET | /api/pedidos | api-access | 200, lista ordenada por ID |
| POST | /api/pedidos | write | 201, pedido creado |
| PATCH | /api/pedidos/{id}/estado | update | 200, pedido actualizado |
| DELETE | /api/pedidos/{id} | delete | 204; 404 si no existe |

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
| Eliminar | wd.eventos.topic | pedido.eliminado |

Los nombres se definen en `application.yml`, enlazados por `MensajeriaProperties` desde `MensajeriaConfig`. La lógica de negocio no conoce nombres de exchanges ni colas. La topología se declara desde ms-operaciones, después de verificar los dos nodos del clúster. Mientras no exista, las publicaciones quedan pendientes.

Contrato JSON: `eventId` (UUID), `tipo` (CREADO, ESTADO_ACTUALIZADO o ELIMINADO), `ocurridoEn` (Instant) y `pedido` (snapshot con id, producto, cliente, direccion, fechaEntrega y estado). Las dos entregas de creación comparten eventId. Cada operación posterior tiene un ID nuevo.

La entrega es **al menos una vez**: un reinicio después del ACK del broker y antes de borrar el registro puede duplicar el evento. Los consumidores deberán manejar duplicados por eventId y dominio. El publicador está diseñado para **una instancia de ms-pedidos**; ejecutar varias requiere coordinación/locking adicional. Si la primera entrega falla, se detiene el lote para no adelantar las posteriores.

Los reintentos de publicación por caída del broker son independientes de los dos reintentos de procesamiento que se implementarán en ms-operaciones. `[ERROR-DLQ]` se acepta y conserva en el evento; su envío a DLQ se implementará y demostrará en la etapa de consumidores.

## Configuración y ejecución

Variables en `.env.example`, sin secretos reales. `.env` está ignorado y **no se carga automáticamente**: exportar las variables o configurarlas en el proceso del servicio.

```powershell
# Desde la raíz del repositorio, después de configurar las variables:
.\mvnw.cmd -pl ms-pedidos spring-boot:run
```

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: PostgreSQL local o RDS. RDS se configurará en despliegue; no se ha conectado desde este trabajo.
- `COGNITO_ISSUER_URI`: emisor autorizado. El arranque real requiere su configuración; los tests lo sustituyen.
- `RABBITMQ_ADDRESSES` (por defecto localhost:5672,localhost:5673), `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`: conexión a los nodos del broker. Los antiguos RABBITMQ_HOST/PORT se aceptan como primer destino cuando no se define RABBITMQ_ADDRESSES.
- `CORS_ALLOWED_ORIGINS`: orígenes separados por comas. Vacío por defecto. Permite GET, POST, PATCH, DELETE y OPTIONS, con Authorization y Content-Type. API Gateway necesitará su configuración coherente en despliegue.
- `DB_DDL_AUTO=update`: crea/actualiza tablas para el proyecto académico. Antes del despliegue definitivo se debe revisar el esquema; no reemplaza un sistema de migraciones.
- `OUTBOX_ENABLED=false`: desactiva el envío programado, pero mantiene el registro de eventos.

## Verificación

`mvn test` desde la raíz ejecuta pruebas de seguridad, CRUD, validación, persistencia JPA, rollback conjunto de pedido/eventos, seed, CORS y envío AMQP confirmado/rechazado/devuelto.

H2 y mocks evitan conexiones externas. No se ha certificado PostgreSQL/RDS, RabbitMQ real ni JVM 17: las pruebas se ejecutan con JDK 21 y compilación `--release 17`. La prueba de recuperación del publicador comprueba que la fila permanece ante fallo y se elimina tras confirmación simulada.

## Defensa en cinco líneas

1. Separé ms-pedidos para darle responsabilidad y despliegue propios.
2. Cambié la lista por JPA/PostgreSQL para conservar pedidos al reiniciar.
3. Validé DTOs y mantuve scopes y CORS configurable para controlar entradas y acceso.
4. Guardé pedido y eventos juntos para recuperar publicaciones si RabbitMQ falla.
5. Probé persistencia, permisos y mensajería localmente; la integración cloud queda pendiente.

Resultado de esta etapa: 22 pruebas backend aprobadas; ng build satisfactorio; 4 pruebas frontend aprobadas. JAR ejecutable empaquetado después de las pruebas.
