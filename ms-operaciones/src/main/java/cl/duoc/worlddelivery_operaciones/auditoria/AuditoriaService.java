package cl.duoc.worlddelivery_operaciones.auditoria;
import cl.duoc.worlddelivery_operaciones.messaging.*;
import cl.duoc.worlddelivery_operaciones.storage.RegistroOperaciones;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class AuditoriaService {
    private static final Logger log=LoggerFactory.getLogger(AuditoriaService.class);
    private final RegistroOperaciones registros;
    public AuditoriaService(RegistroOperaciones registros) { this.registros=registros; }
    public void procesar(EventoPedido evento) {
        boolean nuevo=registros.guardar(Dominio.AUDITORIA,evento);
        log.info("{} eventId={} pedidoId={}",nuevo ? "AUDITORIA_REGISTRADA" : "DUPLICADO_OMITIDO_AUDITORIA",evento.eventId(),evento.pedido().id());
    }
}
