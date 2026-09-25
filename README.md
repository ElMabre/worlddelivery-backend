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
server.port=8888
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://cognito-idp.<REGION>.amazonaws.com/<USER_POOL_ID>
```

## Ejecución Local (Desarrollo)

Para levantar el servicio en tu entorno local, ejecuta:

```bash
./mvnw spring-boot:run
```

La API estará disponible en `http://localhost:8888/api/pedidos`. Cualquier petición sin un Bearer Token válido o sin el scope requerido será rechazada con un código HTTP 401 o 403.

## Empaquetado y Despliegue en AWS (EC2)

El flujo de despliegue utiliza S3 para transferir el artefacto compilado a la instancia EC2 sin exponer credenciales estáticas.

1. Empaquetar el proyecto (equipo local):

   ```bash
   ./mvnw clean package
   ```

2. Subir el artefacto a S3 (equipo local o consola web):

   ```bash
   aws s3 cp target/worlddelivery-backend-0.0.1-SNAPSHOT.jar s3://<TU_BUCKET_S3>/
   ```

3. Descargar y ejecutar (instancia EC2):

   ```bash
   # Detener el proceso Java en ejecución
   pkill -f java

   # Descargar el nuevo .jar desde el bucket
   aws s3 cp s3://<TU_BUCKET_S3>/worlddelivery-backend-0.0.1-SNAPSHOT.jar ./worlddelivery-backend-0.0.1-SNAPSHOT.jar

   # Levantar el servicio en segundo plano
   nohup java -jar worlddelivery-backend-0.0.1-SNAPSHOT.jar &
   ```
