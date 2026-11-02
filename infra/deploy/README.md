# Despliegue y cierre de integración

Esta guía entrega comandos para ejecutar manualmente. No se ha creado ni modificado infraestructura AWS. Las plantillas crean recursos nuevos con costo: no modifican tu API Gateway anterior. Identificar primero si se reutilizará esa API o se desplegará la propuesta HTTP API. No ejecutar ambos caminos accidentalmente.

## 1. Artefactos y requisitos

Tres JARs en los directorios target de ms-pedidos, ms-operaciones y ms-rabbit-admin; frontend en dist/worlddelivery-front/browser. Compilar después de configurar los environments de producción. JVM compatible con Java 17 en EC2, Docker Engine con Compose, systemd y salida HTTPS a Cognito/JWKS. Las pruebas se ejecutaron con JVM 21 y release 17: probar también el runtime elegido en EC2.

```powershell
# Raíz backend
.\mvnw.cmd test
.\mvnw.cmd package
# Raíz frontend
npm ci
npm run build
npm test -- --watch=false
```

Reservar memoria para tres JVM y dos RabbitMQ además del sistema; -Xmx limita el heap, no toda la RAM. Las unidades usan 256 MiB de heap por JVM y límites de metaspace/code cache/direct memory. Medir con systemctl status y docker stats; no prometer funcionamiento en una instancia de 1 GiB. Ambos nodos en una EC2 permiten demostrar el clúster, pero no toleran perder el host; dos réplicas quorum tampoco toleran perder un nodo.

## 2. PostgreSQL RDS

Usar una instancia PostgreSQL privada en la misma VPC, subnets en dos AZ y security group que permita 5432 solo desde el grupo de EC2. Activar cifrado, backups y TLS. Elegir la clase y versión disponibles en tu cuenta; no incluir la contraseña en Git ni en comandos de historial.

Ejemplo PowerShell para una instancia nueva, sustituyendo los identificadores públicos:

```powershell
$region='us-east-1'
$rdsClass='REEMPLAZAR_CLASE'
$subnetGroup='REEMPLAZAR_GRUPO_SUBREDES_RDS'
$rdsSecurityGroup='sg-REEMPLAZAR'
aws rds create-db-instance --region $region --db-instance-identifier worlddelivery-db --engine postgres --db-instance-class $rdsClass --allocated-storage 20 --storage-encrypted --master-username wd_owner --manage-master-user-password --db-name worlddelivery --db-subnet-group-name $subnetGroup --vpc-security-group-ids $rdsSecurityGroup --no-publicly-accessible --backup-retention-period 7
aws rds wait db-instance-available --region $region --db-instance-identifier worlddelivery-db
aws rds describe-db-instances --region $region --db-instance-identifier worlddelivery-db --query 'DBInstances[0].Endpoint.Address' --output text
```

Si RDS ya existe, consultar su endpoint y reutilizarlo; no ejecutar create. Obtener la clave gestionada de forma privada y crear un usuario de aplicación dedicado con permisos sobre la base worlddelivery. Descargar el bundle CA oficial de RDS e instalarlo en /opt/worlddelivery/rds-ca.pem (644), legible por el servicio. /etc/worlddelivery permanece cerrado para proteger los EnvironmentFile.

En una sesión psql autenticada privadamente como propietario, para una base nueva:

```sql
CREATE ROLE wd_app LOGIN;
\password wd_app
\connect worlddelivery
GRANT CONNECT ON DATABASE worlddelivery TO wd_app;
GRANT USAGE, CREATE ON SCHEMA public TO wd_app;
```

La orden interactiva password solicita la clave sin escribirla en el archivo SQL. No ejecutar CREATE ROLE si el usuario ya existe. Si se reutilizan tablas de otra aplicación, revisar propietario y permisos de tablas/secuencias antes de arrancar.

DB_URL usa `jdbc:postgresql://ENDPOINT:5432/worlddelivery?sslmode=verify-full&sslrootcert=/opt/worlddelivery/rds-ca.pem`. DB_USERNAME y DB_PASSWORD van en el EnvironmentFile root-only. Inicializar el esquema con DB_DDL_AUTO=update en la primera puesta en marcha controlada; después detener, cambiar a validate y reiniciar. Este proyecto no incluye migraciones versionadas: para evolución productiva se requiere incorporarlas. SEED_ENABLED=false evita datos de demostración automáticos. Comprobar que pedidos y eventos_pendientes existen y que un pedido sobrevive a reiniciar el servicio.

Después de instalar las unidades (sección siguiente), descargar la CA en EC2:

```bash
curl -fsSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o /tmp/worlddelivery-rds-ca.pem
sudo install -m 644 /tmp/worlddelivery-rds-ca.pem /opt/worlddelivery/rds-ca.pem
```

[TLS PostgreSQL RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).

## 3. EC2: RabbitMQ y servicios

Copiar el backend con sus tres JARs y los archivos de infra; no transferir .env de desarrollo, credenciales ni el directorio .git. En Linux, crear una configuración local de RabbitMQ con usuario dedicado y cookie aleatoria; ambos nodos usan los mismos valores. Mantener los puertos AMQP y Management ligados a localhost como en Compose.

```bash
cd /ruta/worlddelivery-backend/infra/rabbitmq-cluster
# Crear .env PRIVADO con RABBITMQ_USERNAME, RABBITMQ_PASSWORD y RABBITMQ_ERLANG_COOKIE.
chmod 600 .env
docker compose up -d --wait
docker compose exec -T rabbitmq-1 rabbitmq-diagnostics check_running
docker compose exec -T rabbitmq-2 rabbitmq-diagnostics check_running
docker compose exec -T rabbitmq-1 rabbitmqctl cluster_status
cd ../..
sudo bash infra/deploy/install-services.sh
sudoedit /etc/worlddelivery/ms-pedidos.env
sudoedit /etc/worlddelivery/ms-operaciones.env
sudoedit /etc/worlddelivery/ms-rabbit-admin.env
sudo systemd-analyze verify /etc/systemd/system/ms-pedidos.service /etc/systemd/system/ms-operaciones.service /etc/systemd/system/ms-rabbit-admin.service
sudo systemctl enable --now ms-operaciones
sudo journalctl -u ms-operaciones -n 60 --no-pager
sudo systemctl enable --now ms-pedidos ms-rabbit-admin
sudo systemctl status ms-pedidos ms-operaciones ms-rabbit-admin --no-pager
```

Arrancar operaciones primero para declarar la topología. `After=docker.service` solo ordena procesos: no acredita disponibilidad del clúster; verificar ambos nodos antes. Si se agotó StartLimitBurst, corregir la configuración y ejecutar systemctl reset-failed antes de reiniciar. La salida a disco de consumidores persiste en /var/lib/worlddelivery/ms-operaciones/data. Logs en journalctl; no imprimir el contenido de EnvironmentFile. Para renovar JARs, detener los tres servicios antes del instalador; conserva .jar.previous. Reversión: detener el servicio, copiar su .jar.previous a .jar y arrancar; no revierte cambios de esquema ni datos.

La plantilla EC2 no está incluida: se reutiliza tu instancia. En sus grupos no dejar 8080/8082 abiertos al público; las reglas de la plantilla siguiente permiten únicamente al ALB. No exponer 8081 ni 5672/5673/15672/15673 a Internet. Si existen reglas públicas anteriores, retirarlas manualmente tras revisar dependencias.

## 4. S3 y CloudFront

frontend-stack.json crea bucket privado con bloqueo público, versionado y cifrado; CloudFront usa OAC y HTTPS. Los errores S3 403/404 responden /index.html con 200 y TTL de error 0. Esta distribución contiene solo la SPA: no aplicar ese fallback a la API, porque ocultaría errores de autenticación.

```powershell
# Desde la raíz backend; comandos para ejecutar por el estudiante.
aws cloudformation validate-template --template-body file://infra/deploy/frontend-stack.json
aws cloudformation deploy --stack-name worlddelivery-web --template-file infra/deploy/frontend-stack.json --region us-east-1
aws cloudformation describe-stacks --stack-name worlddelivery-web --region us-east-1 --query 'Stacks[0].Outputs'
```

Guardar BucketName, DistributionId y FrontendOrigin. El bucket tiene Retain: borrar el stack no elimina objetos ni versiones. Un 403 por política incorrecta también puede activar el fallback: probar /index.html antes de probar rutas profundas.

[OAC y acceso privado](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html), [respuestas de error](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/custom-error-pages-procedure.html).

## 5. API Gateway

api-stack.json crea una HTTP API nueva, VPC Link, ALB interno y target groups hacia tu EC2 en 8080 y 8082. Requiere dos subnets de distintas AZ en la misma VPC y que el grupo indicado esté asociado a EC2. No crea RDS, EC2 ni Cognito. El health check espera 401 sin token: acredita HTTP y filtros, no salud de RDS o RabbitMQ. El tramo privado ALB→EC2 es HTTP dentro de la VPC; API pública HTTPS.

```powershell
$vpc='vpc-REEMPLAZAR'
$subnets='subnet-REEMPLAZAR1,subnet-REEMPLAZAR2'
$instance='i-REEMPLAZAR'
$backendGroup='sg-REEMPLAZAR'
$issuer='https://cognito-idp.us-east-1.amazonaws.com/REEMPLAZAR_POOL'
$client='REEMPLAZAR_CLIENT_ID'
$front='https://REEMPLAZAR.cloudfront.net'
aws cloudformation validate-template --template-body file://infra/deploy/api-stack.json
aws cloudformation deploy --stack-name worlddelivery-api --template-file infra/deploy/api-stack.json --region us-east-1 --parameter-overrides "VpcId=$vpc" "SubnetIds=$subnets" "InstanceId=$instance" "BackendSecurityGroupId=$backendGroup" "Issuer=$issuer" "ClientId=$client" "FrontendOrigin=$front"
aws cloudformation describe-stacks --stack-name worlddelivery-api --region us-east-1 --query 'Stacks[0].Outputs'
```

| Rutas | Scope |
|---|---|
| GET /api/pedidos | resource-server-worlddelivery/api-access |
| POST /api/pedidos | resource-server-worlddelivery/write |
| PATCH /api/pedidos/{id}/estado | resource-server-worlddelivery/update |
| DELETE /api/pedidos/{id} | resource-server-worlddelivery/delete |
| Las siete rutas del administrador /api/rabbitmq/... | resource-server-worlddelivery/admin |

Se conservan Authorization y query parameters. La integración sobrescribe el path con $request.path para no enviar /prod al backend. CORS admite GET, POST, PATCH, DELETE, OPTIONS y Authorization/Content-Type para el origen CloudFront indicado. No hay ruta $default protegida que capture OPTIONS: la HTTP API responde preflight automáticamente.

Si conservas tu REST API anterior, no ejecutar api-stack.json: configurar recursos /api/pedidos y /api/rabbitmq, métodos y proxy/integraciones hacia los servicios correctos, authorizer Cognito y scopes equivalentes; desplegar cambios al stage existente. En REST API, configurar también OPTIONS sin autorización y CORS en respuestas reales y Gateway Responses 4xx/5xx. No mezclar comandos apigatewayv2 con una REST API.

[Integraciones privadas](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html), [CORS HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html), [JWT authorizers](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html).

## 6. Configurar y publicar Angular

En environment.production.ts, usar ApiBaseUrl + /api/pedidos y + /api/rabbitmq. Redirects: FrontendOrigin + /, con barra final. En CORS de ambos backends usar FrontendOrigin sin barra final. Verificar pool/client/domain públicos y recompilar. No sustituir client ID por un client secret.

```powershell
# Desde la raíz frontend; sustituir identificadores de los outputs.
npm run build
if (Get-ChildItem dist/worlddelivery-front/browser -Filter *.js | Select-String -SimpleMatch 'REEMPLAZAR') { throw 'Falta configurar producción' }
$bucket='REEMPLAZAR_BUCKET'
$distribution='REEMPLAZAR_DISTRIBUTION_ID'
aws s3 sync dist/worlddelivery-front/browser "s3://$bucket" --exclude index.html --cache-control 'public,max-age=86400'
aws s3 cp dist/worlddelivery-front/browser/index.html "s3://$bucket/index.html" --cache-control 'no-cache' --content-type 'text/html'
aws cloudfront create-invalidation --distribution-id $distribution --paths '/*'
```

No usar --delete durante una entrega: clientes con index anterior pueden seguir solicitando chunks antiguos. Verificar /, /pedidos y /rabbit-admin mediante recarga directa. Las rutas privadas deben ir al login sin token.

## 7. Cognito, al final como acordamos

Todavía no se ha modificado Cognito ni probado login real. Conservar los scopes existentes, añadir admin al resource server y configurar el app client público con Authorization Code, scopes admitidos y callbacks/sign-out exactos para localhost y CloudFront. No usar aws.cognito.signin.user.admin como sustituto del custom scope.

Una autorización por scope distingue permisos, pero habilitar admin en un app client no limita qué usuarios lo solicitan. Antes de uso real, restringir su emisión mediante la política de Cognito o añadir un control de grupo admin en backend y probar un usuario sin privilegios. Esa decisión de roles y su configuración quedan para el cierre Cognito; no afirmar que un cliente compartido ya impone roles por usuario.

Comandos de inspección para ejecutar privadamente al iniciar esa etapa:

```powershell
aws cognito-idp describe-resource-server --user-pool-id REEMPLAZAR_POOL --identifier resource-server-worlddelivery --query 'ResourceServer.Scopes'
aws cognito-idp describe-user-pool-client --user-pool-id REEMPLAZAR_POOL --client-id REEMPLAZAR_CLIENT --query 'UserPoolClient.{ClientId:ClientId,Callbacks:CallbackURLs,Logout:LogoutURLs,Scopes:AllowedOAuthScopes,Flows:AllowedOAuthFlows}'
```

No se entrega un update-user-pool-client parcial que pueda restablecer parámetros existentes; preparar la actualización completa a partir de la configuración real en esa etapa. No enviar tokens ni secretos al chat.

## 8. Prueba integrada final y evidencias

1. Login/logout y recarga profunda de rutas; verificar callbacks sin bucles.
2. Sin token: 401. Token válido sin scope: 403. Admin autorizado: crear/consultar/borrar recursos demo sin tocar las seis colas de negocio.
3. Crear pedido por frontend, verificar fila en PostgreSQL, salida de eventos_pendientes después del publisher confirm, ticket y auditoría con mismo eventId.
4. PATCH de estado: notificación simulada y auditoría. DELETE: auditoría del evento eliminado.
5. Con DEMO_FAILURE_ENABLED=true, crear pedido marcado [ERROR-DLQ]: logs con dos reintentos y mensajes en DLQ de despacho y auditoría. Devolver flag a false al terminar.
6. Reiniciar ms-pedidos y ms-operaciones; verificar persistencia y que las redeliveries no duplican archivos. Verificar clúster y quorum_status de las seis colas.
7. Guardar capturas y salidas sin Authorization, cookies o contraseñas. La prueba de PostgreSQL real/outbox todavía no está acreditada por los tests H2.

Verificación local de las plantillas: `python infra/deploy/validate-infra.py` comprueba JSON, referencias, rutas, scopes, CORS, fallback y límites de memoria. No sustituye cloudformation validate-template, la creación real de recursos ni systemd-analyze verify en Linux. Las plantillas no se han validado contra el servicio AWS.

## Defensa en cinco líneas

1. Preparé tres unidades systemd con memoria acotada y configuración externa.
2. Separé PostgreSQL privado y mensajería local de las rutas públicas protegidas.
3. Documenté API Gateway con scopes por operación y CORS que incluye PATCH y DELETE.
4. Preparé S3 privado y CloudFront con fallback para recargar rutas Angular.
5. Dejé un orden de verificación y un guion de defensa; AWS y Cognito reales se ejecutan al cierre.
