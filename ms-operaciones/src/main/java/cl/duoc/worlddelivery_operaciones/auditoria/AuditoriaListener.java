package cl.duoc.worlddelivery_operaciones.auditoria;
import cl.duoc.worlddelivery_operaciones.messaging.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;
import java.io.IOException;
@Component
public class AuditoriaListener {
    private final ProcesadorMensaje procesador;
    private final AuditoriaService servicio;
    public AuditoriaListener(ProcesadorMensaje procesador,AuditoriaService servicio) { this.procesador=procesador; this.servicio=servicio; }
    @RabbitListener(id="auditoria",queues="${app.topology.auditoria.queue}",ackMode="MANUAL",concurrency="1")
    public void recibir(Message message,Channel channel) throws IOException {
        procesador.procesar(Dominio.AUDITORIA,message,channel,servicio::procesar);
    }
}
