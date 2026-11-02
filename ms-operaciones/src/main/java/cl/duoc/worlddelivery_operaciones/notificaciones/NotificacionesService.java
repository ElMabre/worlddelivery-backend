package cl.duoc.worlddelivery_operaciones.notificaciones;
import cl.duoc.worlddelivery_operaciones.messaging.*;
import cl.duoc.worlddelivery_operaciones.storage.RegistroOperaciones;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class NotificacionesService {
    private static final Logger log=LoggerFactory.getLogger(NotificacionesService.class);
    private final RegistroOperaciones registros;
    public NotificacionesService(RegistroOperaciones registros) { this.registros=registros; }
    public void procesar(EventoPedido evento) {
        boolean nuevo=registros.guardar(Dominio.NOTIFICACIONES,evento);
        log.info("{} eventId={} pedidoId={}",nuevo ? "NOTIFICACION_SIMULADA" : "DUPLICADO_OMITIDO_NOTIFICACIONES",evento.eventId(),evento.pedido().id());
    }
}
