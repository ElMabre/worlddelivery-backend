# World Delivery | Despliegue

La instalación de referencia usa EC2 para los tres procesos Java y el clúster RabbitMQ, RDS PostgreSQL en la VPC y una REST API de API Gateway en el stage `Deploy1_Ev`. Cognito autentica las solicitudes del frontend.

## Servicios en EC2

`install-services.sh` instala los JAR en `/opt/worlddelivery` y las unidades de `systemd/`. Los archivos de configuración de los procesos residen en `/etc/worlddelivery`. Mantenerlos accesibles solo para root. Los resultados de operaciones se guardan en `/var/lib/worlddelivery/ms-operaciones/data`.

El broker debe estar disponible antes de arrancar `ms-operaciones`, que declara la topología. Iniciar después `ms-pedidos` y `ms-rabbit-admin`. Para consultar el estado:

```bash
sudo systemctl status ms-operaciones ms-pedidos ms-rabbit-admin --no-pager
```

## PostgreSQL y TLS

RDS es privado. Su grupo de seguridad permite TCP 5432 desde el grupo de EC2. `DB_URL` utiliza el nombre DNS del endpoint de RDS y verifica el certificado con la CA legible por el usuario del servicio:

```text
jdbc:postgresql://ENDPOINT:5432/worlddelivery?sslmode=verify-full&sslrootcert=/opt/worlddelivery/rds-ca.pem
```

Usar `DB_DDL_AUTO=update` en la inicialización controlada de una base nueva. En los siguientes arranques usar `validate`. Las credenciales se configuran en EC2.

## API y frontend

La REST API existente utiliza un autorizador Cognito e integraciones HTTP proxy para `/api/pedidos` y `/api/rabbitmq`. Todo cambio a sus recursos, métodos o Gateway Responses requiere un nuevo deployment en `Deploy1_Ev`. Configurar CORS tanto en las respuestas reales de Spring como en OPTIONS y las respuestas de error generadas por Gateway.

`frontend-stack.json` define un bucket privado con versionado y una distribución de CloudFront con OAC. Publicar el contenido de `dist/worlddelivery-front/browser`, con los assets antes de `index.html`. La distribución sirve la SPA en rutas como `/pedidos` y `/rabbit-admin`. El origen HTTPS del frontend debe coincidir con los callbacks y las URL de logout de Cognito, y con los orígenes permitidos de CORS.

`api-stack.json` define una arquitectura alternativa con una HTTP API, VPC Link y ALB. Desplegarla crea infraestructura adicional independiente de la REST API del proyecto.

## Comprobaciones

`python validate-infra.py` revisa las plantillas localmente. Para validar la integración desplegada, comprobar login, CRUD, persistencia tras reiniciar, entrega del mismo evento de creación en despacho y auditoría, y creación y eliminación de recursos de prueba por la API administrativa.
