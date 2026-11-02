package cl.duoc.worlddelivery_operaciones.despacho;
import cl.duoc.worlddelivery_operaciones.messaging.*;
import cl.duoc.worlddelivery_operaciones.storage.RegistroOperaciones;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class DespachoService {
    private static final Logger log=LoggerFactory.getLogger(DespachoService.class);
    private final RegistroOperaciones registros;
    public DespachoService(RegistroOperaciones registros) { this.registros=registros; }
    public void procesar(EventoPedido evento) {
        boolean nuevo=registros.guardar(Dominio.DESPACHO,evento);
        log.info("{} eventId={} pedidoId={}",nuevo ? "TICKET_GENERADO" : "DUPLICADO_OMITIDO_DESPACHO",evento.eventId(),evento.pedido().id());
    }
}
