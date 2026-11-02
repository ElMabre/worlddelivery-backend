package cl.duoc.worlddelivery_operaciones;
import cl.duoc.worlddelivery_operaciones.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.amqp.core.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class RabbitTopologyTests {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(RabbitTopologyConfig.class)
            .withPropertyValues("app.topology.declare-on-startup=false")
            .withInitializer(context -> {
                try {
                    var sources=new YamlPropertySourceLoader().load("topology",new ClassPathResource("application.yml"));
                    sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
            });
    }
    @Test void declaresSixDurableQuorumQueuesWithTwoReplicas() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            var queues=context.getBeansOfType(Queue.class).values();
            assertThat(queues).hasSize(6);
            for(var q:queues) {
                assertThat(q.isDurable()).isTrue();
                assertThat(q.isExclusive()).isFalse();
                assertThat(q.isAutoDelete()).isFalse();
                assertThat(q.getArguments()).containsEntry("x-queue-type","quorum")
                    .containsEntry("x-quorum-initial-group-size",2)
                    .containsEntry("x-overflow","reject-publish");
            }
        });
    }
    @Test void declaresDirectTopicAndDeadLetterExchanges() {
        runner().run(context -> {
            assertThat(context.getBeansOfType(Exchange.class)).hasSize(3);
            assertThat(context.getBean("pedidosDirect",Exchange.class).getType()).isEqualTo("direct");
            assertThat(context.getBean("eventosTopic",Exchange.class).getType()).isEqualTo("topic");
            assertThat(context.getBean("deadLetterExchange",Exchange.class).getType()).isEqualTo("direct");
            context.getBeansOfType(Exchange.class).values().forEach(e -> assertThat(e.isDurable()).isTrue());
        });
    }
    @Test void connectsEachDomainToItsOwnTerminalDlq() {
        runner().run(context -> {
            var config=context.getBean(TopologiaProperties.class);
            var queues=context.getBeansOfType(Queue.class).values();
            var bindings=context.getBeansOfType(Binding.class).values();
            assertThat(bindings).hasSize(6);
            for(var route:List.of(config.despacho(),config.notificaciones(),config.auditoria())) {
                var main=queues.stream().filter(q -> q.getName().equals(route.queue())).findFirst().orElseThrow();
                var dlq=queues.stream().filter(q -> q.getName().equals(route.deadLetterQueue())).findFirst().orElseThrow();
                assertThat(main.getArguments()).containsEntry("x-dead-letter-exchange",config.deadLetterExchange())
                    .containsEntry("x-dead-letter-routing-key",route.deadLetterKey())
                    .containsEntry("x-dead-letter-strategy","at-least-once");
                assertThat(dlq.getArguments()).doesNotContainKeys("x-dead-letter-exchange","x-message-ttl");
                assertThat(bindings.stream().filter(b -> b.getExchange().equals(config.deadLetterExchange())
                    && b.getDestination().equals(route.deadLetterQueue()) && b.getRoutingKey().equals(route.deadLetterKey())).count()).isEqualTo(1);
                String source=route.equals(config.despacho()) ? config.directExchange() : config.topicExchange();
                assertThat(bindings.stream().filter(b -> b.getExchange().equals(source)
                    && b.getDestination().equals(route.queue()) && b.getRoutingKey().equals(route.routingKey())).count()).isEqualTo(1);
            }
        });
    }
    @Test void changingPropertiesChangesQueuesAndBindingsTogether() {
        runner().withPropertyValues("app.topology.despacho.queue=custom.queue",
            "app.topology.despacho.dead-letter-queue=custom.dlq",
            "app.topology.despacho.routing-key=custom.created",
            "app.topology.despacho.dead-letter-key=custom.dead",
            "app.topology.direct-exchange=custom.direct",
            "app.topology.dead-letter-exchange=custom.dlx").run(context -> {
                assertThat(context).hasNotFailed();
                var binding=context.getBean("despachoBinding",Binding.class);
                assertThat(binding.getDestination()).isEqualTo("custom.queue");
                assertThat(binding.getExchange()).isEqualTo("custom.direct");
                assertThat(binding.getRoutingKey()).isEqualTo("custom.created");
                assertThat(context.getBean("despachoQueue",Queue.class).getArguments())
                    .containsEntry("x-dead-letter-exchange","custom.dlx").containsEntry("x-dead-letter-routing-key","custom.dead");
                assertThat(context.getBean("despachoDlqBinding",Binding.class).getDestination()).isEqualTo("custom.dlq");
            });
    }
    @ParameterizedTest
    @ValueSource(strings={"app.topology.despacho.queue=", "app.topology.replicas=0",
        "app.topology.notificaciones.queue=q.despacho.tickets", "app.topology.auditoria.dead-letter-key=despacho.dead"})
    void rejectsInvalidOrAmbiguousTopology(String property) {
        runner().withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }
    @Test void publisherAndTopologyAgreeOnContract() throws Exception {
        var publisher=new YamlPropertySourceLoader().load("publisher",new FileSystemResource(
            "../ms-pedidos/src/main/resources/application.yml")).get(0);
        runner().run(context -> {
            var config=context.getBean(TopologiaProperties.class);
            assertThat(config.directExchange()).isEqualTo(publisher.getProperty("app.messaging.direct-exchange"));
            assertThat(config.topicExchange()).isEqualTo(publisher.getProperty("app.messaging.topic-exchange"));
            assertThat(config.despacho().routingKey()).isEqualTo(publisher.getProperty("app.messaging.created-key"));
            assertThat(config.notificaciones().routingKey()).isEqualTo(publisher.getProperty("app.messaging.state-prefix")+"*");
            assertThat(config.auditoria().routingKey()).isEqualTo("pedido.#");
        });
    }
    @Test void startupDeclaresExchangesBeforeQueuesAndBindings() {
        var admin=mock(AmqpAdmin.class);
        when(admin.declareQueue(any())).thenAnswer(i -> ((Queue)i.getArgument(0)).getName());
        runner().withPropertyValues("app.topology.declare-on-startup=true")
            .withBean(AmqpAdmin.class,() -> admin).run(context -> {
                context.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments());
                var order=inOrder(admin);
                order.verify(admin,times(3)).declareExchange(any());
                order.verify(admin,times(6)).declareQueue(any());
                order.verify(admin,times(6)).declareBinding(any());
            });
    }
}
