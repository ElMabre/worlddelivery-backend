# World Delivery - Backend

API REST construida con Spring Boot para la plataforma de logística World Delivery. Este servicio actúa como un Resource Server de OAuth 2.0, validando tokens JWT emitidos por Amazon Cognito para proteger el acceso a los datos.

## Arquitectura y Tecnologías

- **Framework:** Spring Boot, Spring Web
- **Seguridad:** Spring Security, OAuth2 Resource Server
- **Gestión de dependencias:** Maven
- **Despliegue:** Amazon EC2
- **Distribución de artefactos:** Amazon S3

## Prerrequisitos

- Java 17 o superior.
- Maven (se incluye Maven Wrapper `mvnw` en el repositorio).
- Un bucket de Amazon S3 y una instancia EC2 con el IAM Role adecuado (para el despliegue en la nube).

## Configuración

Antes de ejecutar el proyecto, asegúrate de que el archivo `src/main/resources/application.properties` tenga correctamente configurada la URI de Cognito y el puerto:

```properties
server.port=8080
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://cognito-idp.<REGION>.amazonaws.com/<USER_POOL_ID>
```

## Ejecución Local (Desarrollo)

Para levantar el servicio en tu entorno local, ejecuta:

```bash
./mvnw spring-boot:run
```

La API estará disponible en `http://localhost:8080/api/pedidos`. Cualquier petición sin un Bearer Token válido o sin el scope requerido será rechazada con un código HTTP 401 o 403.

## Etapa 1: base compilando y tests

Rama: `ep3-rabbitmq`. El ZIP original no incluía historial Git.

En Windows: `mvn test` o `./mvnw.cmd test`. En Linux/macOS: `./mvnw test`.

Siete pruebas con el contexto Spring y MockMvc verifican: 401 sin token, 401 ante token inválido, 403 sin scope de lectura, lectura autorizada, rechazo de modificaciones con scope de lectura, ciclo crear/actualizar/eliminar autorizado, y 404 al actualizar un pedido inexistente.

JwtDecoder se sustituye por un mock y el issuer de pruebas es https://issuer.example.test. No se contacta a Cognito. Los JWT simulados prueban autorización, no certifican firmas criptográficas ni el estado de AWS.

Mockito se carga como agente al iniciar la JVM mediante Surefire. Maven resuelve su ubicación con maven-dependency-plugin, evitando el bloqueo de adjuntar agentes dinámicamente en Windows.

Compilación configurada para Java 17. Verificación ejecutada con JDK 21.0.11 y --release 17; la ejecución sobre JVM 17 está pendiente.

## Configuración sensible y despliegue

`.env`, `.env.*`, logs y target están excluidos de Git. `.env.example` solo puede contener marcadores. Las futuras credenciales de PostgreSQL y RabbitMQ se inyectarán mediante variables de entorno.

La guía de despliegue para los tres servicios se incorporará en su etapa. No se ejecutaron comandos AWS. Los pedidos continúan en memoria hasta la etapa ms-pedidos.
