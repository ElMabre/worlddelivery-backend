# World Delivery - Backend

Repositorio de microservicios independientes Spring Boot 4.1.1, Java 17. Rama de trabajo: `ep3-rabbitmq`.

| Servicio | Puerto | Estado |
|---|---|---|
| ms-pedidos | 8080 | JPA/PostgreSQL, DTOs, JWT, registro transaccional y productor AMQP |
| ms-operaciones | 8081 | Topología, tres consumidores, ACK manual, reintentos y DLQ |
| ms-rabbit-admin | 8082 | API REST, RabbitAdmin encapsulado, DTOs validados y JWT con scope admin |

El POM raíz agrega los módulos implementados. Cada servicio hereda directamente de Spring Boot y puede compilarse independientemente.

## Compilar y probar

Desde esta carpeta:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Con Maven instalado: `mvn test` y `mvn package`. En Linux: `bash mvnw test`.

Las pruebas usan H2 en modo PostgreSQL, JwtDecoder simulado y publicación AMQP simulada. No contactan a AWS ni necesitan credenciales. H2 no sustituye la verificación de integración sobre PostgreSQL real.

Documentación del servicio: [ms-pedidos](ms-pedidos/README.md).

## Etapas y alcance

1. Base: pruebas Angular y Spring aisladas; lockfile de npm; exclusión de secretos y logs.
2. ms-pedidos: persistencia JPA, validación, CORS configurable, productor y eventos pendientes.
3. Clúster y topología: [Compose y verificación](infra/rabbitmq-cluster/README.md), exchanges y seis colas quorum. Dos nodos y seis colas con dos miembros verificados mediante salida aportada por el estudiante.
4. [Consumidores](ms-operaciones/README.md): resultados locales por dominio, reintentos, deduplicación y guion de demo; prueba real de consumo/DLQ confirmada por las salidas del estudiante.
5. [Administrador](ms-rabbit-admin/README.md): crear/eliminar colas, exchanges y bindings; consulta por nombre, JWT, scope admin y CORS configurable. Integración real con token y broker pendiente.
6. Frontend modular implementado en su repositorio: shell, guards, vistas de pedidos y RabbitMQ, interfaces y environments.
7. [Despliegue](infra/deploy/README.md): unidades systemd, guía RDS, plantillas S3/CloudFront y HTTP API, comandos manuales y [guion EP4 de 8 minutos](infra/deploy/EP4-guion.md).

No se ejecutaron acciones contra AWS. Quedan pendientes Cognito real, permisos por usuario, integración completa con PostgreSQL y ejecución del despliegue. Las instrucciones están entregadas como comandos para el estudiante. Los archivos de entorno y logs están ignorados; solo ejemplos con marcadores se versionan.
