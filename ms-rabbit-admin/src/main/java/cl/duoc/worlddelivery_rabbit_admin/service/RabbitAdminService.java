package cl.duoc.worlddelivery_rabbit_admin.service;
import cl.duoc.worlddelivery_rabbit_admin.api.Requests.*;
import cl.duoc.worlddelivery_rabbit_admin.config.AdminProperties;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class RabbitAdminService {
    private final RabbitAdmin admin;
    private final AdminProperties config;
    public RabbitAdminService(RabbitAdmin admin,AdminProperties config) { this.admin=admin; this.config=config; }
    public QueueRequest createQueue(QueueRequest request) {
        var queue=QueueBuilder.durable(request.name()).quorum()
            .withArgument("x-quorum-initial-group-size",config.replicas())
            .withArgument("x-max-length",config.maxLength()).withArgument("x-overflow","reject-publish").build();
        if(admin.declareQueue(queue)==null) throw unavailable();
        return request;
    }
    public ExchangeRequest createExchange(ExchangeRequest request) {
        Exchange exchange=switch(request.type()) {
            case DIRECT -> new DirectExchange(request.name(),true,false);
            case TOPIC -> new TopicExchange(request.name(),true,false);
            case FANOUT -> new FanoutExchange(request.name(),true,false);
        };
        admin.declareExchange(exchange); return request;
    }
    public BindingRequest createBinding(BindingRequest request) { admin.declareBinding(binding(request)); return request; }
    public void deleteBinding(BindingRequest request) { admin.removeBinding(binding(request)); }
    public void deleteQueue(String name) { if(!admin.deleteQueue(name)) throw unavailable(); }
    public void deleteExchange(String name) { if(!admin.deleteExchange(name)) throw unavailable(); }
    public QueueStatus queueStatus(String name) {
        var properties=admin.getQueueProperties(name);
        if(properties==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cola inexistente o no consultable");
        return new QueueStatus(name,((Number)properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue(),
            ((Number)properties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).intValue());
    }
    private Binding binding(BindingRequest request) {
        return new Binding(request.queue(),Binding.DestinationType.QUEUE,request.exchange(),request.routingKey(),null);
    }
    private ResponseStatusException unavailable() {
        // RabbitAdmin can return false/null on broker failures; do not report a fabricated success or 404.
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"RabbitMQ no confirmo la operacion; verificar el recurso antes de repetir");
    }
}
