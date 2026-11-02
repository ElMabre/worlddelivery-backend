package cl.duoc.worlddelivery_operaciones.config;
import org.springframework.amqp.core.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
@Configuration
@EnableConfigurationProperties(TopologiaProperties.class)
public class RabbitTopologyConfig {
    private final TopologiaProperties config;
    public RabbitTopologyConfig(TopologiaProperties config) { this.config=config; }
    @Bean DirectExchange pedidosDirect() { return new DirectExchange(config.directExchange(),true,false); }
    @Bean TopicExchange eventosTopic() { return new TopicExchange(config.topicExchange(),true,false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(config.deadLetterExchange(),true,false); }

    @Bean Queue despachoQueue() { return operativa(config.despacho()); }
    @Bean Queue notificacionesQueue() { return operativa(config.notificaciones()); }
    @Bean Queue auditoriaQueue() { return operativa(config.auditoria()); }
    @Bean Queue despachoDlq() { return quorum(config.despacho().deadLetterQueue()).build(); }
    @Bean Queue notificacionesDlq() { return quorum(config.notificaciones().deadLetterQueue()).build(); }
    @Bean Queue auditoriaDlq() { return quorum(config.auditoria().deadLetterQueue()).build(); }

    @Bean Binding despachoBinding(@Qualifier("despachoQueue") Queue queue,
                                  @Qualifier("pedidosDirect") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(config.despacho().routingKey());
    }
    @Bean Binding notificacionesBinding(@Qualifier("notificacionesQueue") Queue queue,
                                       @Qualifier("eventosTopic") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(config.notificaciones().routingKey());
    }
    @Bean Binding auditoriaBinding(@Qualifier("auditoriaQueue") Queue queue,
                                  @Qualifier("eventosTopic") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(config.auditoria().routingKey());
    }
    @Bean Binding despachoDlqBinding(@Qualifier("despachoDlq") Queue queue,
                                    @Qualifier("deadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(config.despacho().deadLetterKey());
    }
    @Bean Binding notificacionesDlqBinding(@Qualifier("notificacionesDlq") Queue queue,
                                         @Qualifier("deadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(config.notificaciones().deadLetterKey());
    }
    @Bean Binding auditoriaDlqBinding(@Qualifier("auditoriaDlq") Queue queue,
                                    @Qualifier("deadLetterExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(config.auditoria().deadLetterKey());
    }
    // Explicit declaration is needed even before the consumer listeners exist.
    @Bean
    @ConditionalOnProperty(name="app.topology.declare-on-startup",havingValue="true",matchIfMissing=true)
    ApplicationRunner declararTopologia(AmqpAdmin admin, List<Exchange> exchanges,
                                        List<Queue> queues, List<Binding> bindings) {
        return args -> {
            for (var exchange:exchanges) admin.declareExchange(exchange);
            for (var queue:queues) {
                if (admin.declareQueue(queue)==null) throw new IllegalStateException("No se pudo declarar la cola " + queue.getName());
            }
            for (var binding:bindings) admin.declareBinding(binding);
        };
    }
    private QueueBuilder quorum(String name) {
        return QueueBuilder.durable(name).quorum()
            .withArgument("x-quorum-initial-group-size",config.replicas())
            .withArgument("x-max-length",config.maxLength())
            .withArgument("x-overflow","reject-publish");
    }
    private Queue operativa(TopologiaProperties.Ruta ruta) {
        return quorum(ruta.queue())
            .deadLetterExchange(config.deadLetterExchange())
            .deadLetterRoutingKey(ruta.deadLetterKey())
            .withArgument("x-dead-letter-strategy","at-least-once").build();
    }
}
