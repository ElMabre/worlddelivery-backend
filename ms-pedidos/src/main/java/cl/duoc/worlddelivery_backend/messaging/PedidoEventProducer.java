package cl.duoc.worlddelivery_backend.messaging;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
@Component
public class PedidoEventProducer {
    private final RabbitTemplate rabbit;
    public PedidoEventProducer(RabbitTemplate rabbit) { this.rabbit=rabbit; }
    public void publicar(EventoPendiente evento) {
        var properties=new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(evento.getEventId());
        var message=new Message(evento.getPayload().getBytes(StandardCharsets.UTF_8),properties);
        var correlation=new CorrelationData(evento.getEventId()+":"+evento.getId());
        try {
            rabbit.send(evento.getExchangeName(),evento.getRoutingKey(),message,correlation);
            var confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
            if (!confirm.ack() || correlation.getReturned()!=null) {
                throw new IllegalStateException("Publicación rechazada o sin ruta");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación interrumpida",ex);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("Sin confirmación del broker",ex);
        }
    }
}
