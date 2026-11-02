package cl.duoc.worlddelivery_backend;
import cl.duoc.worlddelivery_backend.messaging.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class PedidoEventProducerTests {
    private EventoPendiente evento() { return new EventoPendiente("test-event","test.exchange","test.key","{}"); }
    @Test void sendsPersistentJsonAndWaitsForConfirmation() {
        var rabbit=mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            Message message=invocation.getArgument(2);
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo("test-event");
            CorrelationData correlation=invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null)); return null;
        }).when(rabbit).send(eq("test.exchange"),eq("test.key"),any(Message.class),any(CorrelationData.class));
        new PedidoEventProducer(rabbit).publicar(evento());
        verify(rabbit).send(eq("test.exchange"),eq("test.key"),any(Message.class),any(CorrelationData.class));
    }
    @Test void rejectsNegativeConfirmation() {
        var rabbit=mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation=invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false,"Rejected")); return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        assertThatThrownBy(() -> new PedidoEventProducer(rabbit).publicar(evento())).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsUnroutableMessageEvenWithPositiveConfirmation() {
        var rabbit=mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation=invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(invocation.getArgument(2),312,"NO_ROUTE","test.exchange","test.key"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null)); return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        assertThatThrownBy(() -> new PedidoEventProducer(rabbit).publicar(evento())).isInstanceOf(IllegalStateException.class);
    }
}
