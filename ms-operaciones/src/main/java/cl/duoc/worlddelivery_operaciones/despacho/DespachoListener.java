package cl.duoc.worlddelivery_operaciones.despacho;
import cl.duoc.worlddelivery_operaciones.messaging.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;
import java.io.IOException;
@Component
public class DespachoListener {
    private final ProcesadorMensaje procesador;
    private final DespachoService servicio;
    public DespachoListener(ProcesadorMensaje procesador,DespachoService servicio) { this.procesador=procesador; this.servicio=servicio; }
    @RabbitListener(id="despacho",queues="${app.topology.despacho.queue}",ackMode="MANUAL",concurrency="1")
    public void recibir(Message message,Channel channel) throws IOException {
        procesador.procesar(Dominio.DESPACHO,message,channel,servicio::procesar);
    }
}
