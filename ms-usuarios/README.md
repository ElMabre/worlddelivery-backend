# World Delivery | Usuarios

API Spring Boot en el puerto 8083 para administrar el User Pool de Cognito mediante AWS SDK for Java v2. El servicio obtiene atributos y grupos directamente de Cognito.

| Método | Ruta | Resultado |
|---|---|---|
| GET | /api/usuarios | Todos los usuarios con atributos, grupos, rol efectivo y estado enabled |
| PATCH | /api/usuarios/{username}/deshabilitar | Deshabilita la cuenta y devuelve 204 |
| PATCH | /api/usuarios/{username}/rol | Recibe {"rol":"Admin"} o {"rol":"Usuario"} y devuelve el usuario actualizado |

Los endpoints requieren un access token del cliente configurado, scope `resource-server-worlddelivery/admin` y grupo `Admin`. El decoder verifica firma, emisor, expiración y client_id.

## Configuración

`AWS_REGION` define la región y `COGNITO_USER_POOL_ID` identifica el pool. `COGNITO_CLIENT_ID` restringe el cliente emisor de los tokens. `CORS_ALLOWED_ORIGINS` contiene los orígenes permitidos separados por comas.

Las credenciales del SDK proceden de las variables AWS del proceso o del instance profile de EC2. El rol IAM necesita ListUsers, AdminListGroupsForUser, AdminGetUser, AdminDisableUser, AdminAddUserToGroup y AdminRemoveUserFromGroup sobre el pool. El JWT de una persona autoriza la petición HTTP y el rol IAM autoriza la llamada del servicio a Cognito.

## Roles y errores

El cambio de rol agrega el grupo solicitado y retira el otro grupo funcional. Los grupos ajenos a Admin/Usuario se conservan. Cognito ejecuta ambas operaciones por separado. Si una falla, la API responde 503 y la interfaz consulta el estado real antes de reintentar. Durante un fallo parcial pueden coexistir ambos grupos y Admin tiene prioridad. Repetir la petición completa converge al rol solicitado.

Se impide deshabilitar la cuenta propia o retirarse el rol Admin, incluso si se usa un alias de correo. Debe existir un primer Admin asignado por un operador autorizado. Los grupos se actualizan en los tokens al renovar la sesión. Los JWT que ya fueron emitidos pueden seguir siendo aceptados por validadores locales hasta su expiración.

`GET /api/usuarios` recorre las páginas de usuarios y las de grupos de cada usuario. Este listado completo es apropiado para el pool pequeño del proyecto. Pools grandes requieren paginación HTTP para evitar el timeout de Gateway.

## Ejecución

Desde la raíz del backend, con las variables configuradas en el proceso:

```powershell
.\mvnw.cmd -pl ms-usuarios spring-boot:run
```

Las pruebas usan un cliente Cognito simulado y comprueban autorización, paginación, cambio de rol, bajas, errores parciales y CORS.
