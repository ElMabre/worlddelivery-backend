# World Delivery - Backend

Repositorio de microservicios independientes Spring Boot 4.1.1, Java 17. Rama de trabajo: `ep3-rabbitmq`.

| Servicio | Puerto | Estado |
|---|---|---|
| ms-pedidos | 8080 | JPA/PostgreSQL, DTOs, JWT, registro transaccional y productor AMQP |
| ms-operaciones | 8081 | Topología declarativa; consumidores pendientes |
| ms-rabbit-admin | 8082 | Pendiente de su etapa |

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
3. Clúster y topología: [Compose y verificación](infra/rabbitmq-cluster/README.md), exchanges y seis colas quorum. Ejecución real de Docker pendiente.
4. Pendiente: consumidores, administrador, frontend modular y despliegue.

No se ejecutan acciones contra AWS. Las instrucciones de despliegue se entregarán como comandos para el estudiante. `.env`, `.env.*` y logs están ignorados; solo `.env.example` con marcadores se versiona.
