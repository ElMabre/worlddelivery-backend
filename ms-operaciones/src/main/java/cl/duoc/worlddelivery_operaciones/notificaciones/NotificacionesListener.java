package cl.duoc.worlddelivery_operaciones.notificaciones;
import cl.duoc.worlddelivery_operaciones.messaging.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;
import java.io.IOException;
@Component
public class NotificacionesListener {
    private final ProcesadorMensaje procesador;
    private final NotificacionesService servicio;
    public NotificacionesListener(ProcesadorMensaje procesador,NotificacionesService servicio) { this.procesador=procesador; this.servicio=servicio; }
    @RabbitListener(id="notificaciones",queues="${app.topology.notificaciones.queue}",ackMode="MANUAL",concurrency="1")
    public void recibir(Message message,Channel channel) throws IOException {
        procesador.procesar(Dominio.NOTIFICACIONES,message,channel,servicio::procesar);
    }
}
