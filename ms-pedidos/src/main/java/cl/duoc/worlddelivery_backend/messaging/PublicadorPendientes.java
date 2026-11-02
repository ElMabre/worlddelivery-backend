package cl.duoc.worlddelivery_backend.messaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
@Component
public class PublicadorPendientes {
    private static final Logger log=LoggerFactory.getLogger(PublicadorPendientes.class);
    private final EventoPendienteRepository pendientes;
    private final PedidoEventProducer productor;
    public PublicadorPendientes(EventoPendienteRepository pendientes, PedidoEventProducer productor) {
        this.pendientes=pendientes; this.productor=productor;
    }
    // Un único publicador por instancia. Entrega al menos una vez: conservar eventId en reenvíos.
    public void publicarLote() {
        for (var evento:pendientes.findTop50ByOrderByIdAsc()) {
            try {
                productor.publicar(evento);
                pendientes.deleteById(evento.getId());
                log.info("Evento confirmado eventId={} routingKey={}",evento.getEventId(),evento.getRoutingKey());
            } catch (RuntimeException ex) {
                log.warn("Evento pendiente eventId={} causa={}",evento.getEventId(),ex.getClass().getSimpleName());
                break;
            }
        }
    }
}
