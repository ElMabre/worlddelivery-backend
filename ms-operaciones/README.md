# ms-operaciones

Spring Boot 4.1.1 / Java 17, puerto 8081. En esta etapa declara la topología. Los listeners por dominio y sus reintentos pertenecen a la siguiente etapa.

`application.yml` centraliza nombres y patrones. `TopologiaProperties` valida campos y rechaza duplicados de colas, exchanges y claves DLQ. `RabbitTopologyConfig` declara beans de Queue, Exchange y Binding; un ApplicationRunner los registra incluso cuando todavía no existen listeners. La declaración falla si el broker no está disponible: no se informa un arranque correcto sin declarar recursos.

## Arranque

Primero levantar y verificar [el clúster](../infra/rabbitmq-cluster/README.md). Deben estar los dos nodos activos antes de declarar las colas.

Exportar las variables de `.env.example` y ejecutar desde la raíz:

```powershell
.\mvnw.cmd -pl ms-operaciones spring-boot:run
```

No se carga .env automáticamente. No contiene endpoints REST propios ni administrador; esa API estará en ms-rabbit-admin.

## Diseño

- Direct: despacho ante creación.
- Topic: notificación de cambios de estado y auditoría de todos los eventos de pedido.
- DLX direct: tres claves separadas, una por DLQ, evitando que un error se copie a las tres.
- Seis colas quorum durables; dos réplicas iniciales; sin auto-delete ni exclusividad.
- Las tres colas principales conservan los mensajes muertos hasta confirmar el envío a la DLQ (at-least-once). Las DLQ son terminales y no expiran mensajes.
- Límite de 10000 mensajes por cola y overflow reject-publish. Un rechazo del broker deja pendiente el evento en ms-pedidos.
- Preparado acknowledge-mode manual y prefetch 1; aún no hay consumidores.

Cambiar nombres requiere revisar también la configuración del productor. Una prueba compara ambos YAML para detectar divergencias. Los tests se ejecutan desde el repositorio completo, donde existe el módulo hermano ms-pedidos.

La lista de direcciones permite reconexión a cualquiera de los nodos; no elimina la necesidad de mayoría de las quorum queues.

## Pruebas

`mvn test` desde la raíz. Pruebas locales sin broker: cantidad/tipo/durabilidad de recursos, bindings, DLQ aisladas, parametrización, rechazo de configuración inválida y orden de declaración. Esto no acredita la ejecución real de Docker ni el traslado real a DLQ.

## Defensa en cinco líneas

1. Preparé dos nodos con nombres estables, volúmenes y una cookie externa compartida.
2. Centralicé la topología en YAML y beans para separar infraestructura y negocio.
3. Definí tres rutas funcionales y una DLQ aislada por cada una.
4. Elegí quorum y confirmación de dead lettering para conservar los mensajes fallidos.
5. Validé configuración y contratos; con dos réplicas no hay tolerancia a la pérdida de un nodo.
