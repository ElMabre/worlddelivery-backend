package cl.duoc.worlddelivery_backend.messaging;
import cl.duoc.worlddelivery_backend.config.MensajeriaProperties;
import cl.duoc.worlddelivery_backend.dto.PedidoResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
@Component
public class RegistroEventos {
    private final EventoPendienteRepository pendientes;
    private final MensajeriaProperties config;
    private final ObjectMapper json;
    public RegistroEventos(EventoPendienteRepository pendientes, MensajeriaProperties config, ObjectMapper json) {
        this.pendientes=pendientes; this.config=config; this.json=json;
    }
    // Obligatoriamente participa en la transacción que guarda o elimina el pedido.
    @Transactional(propagation=Propagation.MANDATORY)
    public void registrar(PedidoEvento.Tipo tipo, PedidoResponse pedido) {
        var evento=new PedidoEvento(UUID.randomUUID().toString(),tipo,Instant.now(),pedido);
        String payload=json.writeValueAsString(evento);
        String key=switch(tipo) {
            case CREADO -> config.createdKey();
            case ELIMINADO -> config.deletedKey();
            case ESTADO_ACTUALIZADO -> config.statePrefix()+pedido.estado().claveEvento();
        };
        if (tipo==PedidoEvento.Tipo.CREADO) {
            pendientes.save(new EventoPendiente(evento.eventId(),config.directExchange(),key,payload));
        }
        // Todos los eventos, incluida creación, llegan al topic de auditoría.
        pendientes.save(new EventoPendiente(evento.eventId(),config.topicExchange(),key,payload));
    }
}
