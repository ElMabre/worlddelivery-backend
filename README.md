# World Delivery | Backend

Proyecto Maven con tres microservicios Spring Boot 4.1.1. Los módulos compilan para Java 17. El flujo de pedidos combina PostgreSQL y RabbitMQ para procesar entregas, notificaciones y auditoría.

| Microservicio | Puerto | Responsabilidad |
|---|---:|---|
| [ms-pedidos](ms-pedidos/README.md) | 8080 | API de pedidos, persistencia JPA y publicación de eventos desde el outbox |
| [ms-operaciones](ms-operaciones/README.md) | 8081 | Declaración de la topología y consumo de eventos por dominio |
| [ms-rabbit-admin](ms-rabbit-admin/README.md) | 8082 | API de administración de colas, exchanges y bindings |

## Flujo de un pedido

`ms-pedidos` guarda el pedido y las entregas pendientes en una misma transacción. Un publicador envía los eventos a RabbitMQ y retira cada entrega del outbox cuando el broker confirma la publicación y no la devuelve por falta de ruta. La creación de un pedido genera dos entregas con el mismo `eventId`: una para despacho y otra para auditoría.

`ms-operaciones` guarda un resultado por dominio y `eventId` antes de confirmar el mensaje. Los errores recuperables admiten hasta tres intentos en la misma entrega. Los mensajes inválidos y los que agotan sus intentos se rechazan sin reencolado y se enrutan a la DLQ del dominio.

## Compilación y pruebas

Desde la raíz del backend, con un JDK 21 para reproducir el entorno de compilación:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Cada módulo genera su JAR en `target/`. Las pruebas usan H2, dobles de Cognito y RabbitMQ y archivos temporales. La validación de RDS y del broker real se realiza por separado.

## Ejecución y despliegue

1. Levantar el [clúster de RabbitMQ](infra/rabbitmq-cluster/README.md) y verificar que ambos nodos estén activos.
2. Configurar las variables de cada proceso según su `application.yml`. Arrancar `ms-operaciones` para declarar la topología.
3. Iniciar `ms-pedidos` con PostgreSQL y después `ms-rabbit-admin` con el scope administrativo configurado.

En AWS, la REST API usa el stage `Deploy1_Ev` e integra las rutas de pedidos y administración con sus servicios en EC2. Cognito emite los access tokens. Las [unidades systemd y plantillas de despliegue](infra/deploy/README.md) están en `infra/deploy/`.

El clúster de dos nodos necesita ambos miembros para mantener el quorum. Los resultados de los consumidores residen en disco local. El modelo de datos usa validación de esquema en operación y no incluye migraciones versionadas.
