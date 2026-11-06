package cl.duoc.worlddelivery_operaciones;

import cl.duoc.worlddelivery_operaciones.config.ProcesamientoProperties;
import cl.duoc.worlddelivery_operaciones.messaging.*;
import cl.duoc.worlddelivery_operaciones.storage.RegistroOperaciones;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.rabbitmq.dynamic=false", "app.topology.declare-on-startup=false",
    "spring.rabbitmq.username=test", "spring.rabbitmq.password=test"})
class ConsumidoresTest {
    final RetryPause pause=mock(RetryPause.class);
    @Autowired cl.duoc.worlddelivery_operaciones.config.RetryProperties retry;
    @Autowired org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory factory;
    @Autowired ObjectMapper json;
    @Autowired Validator validator;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @TempDir Path temporal;
    EventoPedido evento(String producto) {
        return new EventoPedido(UUID.randomUUID(),EventoPedido.Tipo.CREADO,Instant.parse("2026-11-02T12:00:00Z"),
            new EventoPedido.Pedido(1L,producto,"Cliente demo","Direccion demo",LocalDate.of(2026,11,3),EventoPedido.Estado.PENDIENTE));
    }
    Message mensaje(byte[] body) {
        var properties=new MessageProperties(); properties.setDeliveryTag(42);
        return new Message(body,properties);
    }
    Message mensaje(EventoPedido e) { return mensaje(json.writeValueAsBytes(e)); }
    ProcesadorMensaje procesador(boolean demo) {
        return new ProcesadorMensaje(json,validator,new ProcesamientoProperties(demo,temporal.toString()),new cl.duoc.worlddelivery_operaciones.config.RetryProperties(true,3,Duration.ofSeconds(3)),pause);
    }
    RegistroOperaciones registro() {
        return new RegistroOperaciones(new ProcesamientoProperties(true,temporal.toString()),json);
    }
    @Test void configuraUnaSolaPoliticaDeTresIntentosConPausa() {
        assertThat(retry.enabled()).isTrue();
        assertThat(retry.attempts()).isEqualTo(3);
        assertThat(retry.initialInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(factory.getAdviceChain()).isEmpty();
    }
    @Test void interrupcionNoConfirmaNiDescartaEntrega() throws Exception {
        var channel=mock(Channel.class);
        doThrow(new InterruptedException()).when(pause).await(any());
        try {
            assertThatThrownBy(() -> procesador(true).procesar(Dominio.DESPACHO,mensaje(evento("Producto")),channel,
                e -> {throw new FalloRecuperable("temporal");})).isInstanceOf(IOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(channel);
        } finally { Thread.interrupted(); }
    }
    @Test void registraTresColasConAckManualSinIniciarConexion() {
        assertThat(listeners.getListenerContainerIds()).containsExactlyInAnyOrder("despacho","notificaciones","auditoria");
        String[] queues={"q.despacho.tickets","q.notificaciones.clientes","q.auditoria.eventos"};
        int index=0;
        for (String id:new String[]{"despacho","notificaciones","auditoria"}) {
            var container=(AbstractMessageListenerContainer)listeners.getListenerContainer(id);
            assertThat(container.isRunning()).isFalse();
            assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
            assertThat(container.getQueueNames()).containsExactly(queues[index++]);
        }
    }
    @Test void confirmaSoloDespuesDeGuardarYOmiteDuplicadoTrasReiniciarRegistro() throws Exception {
        var e=evento("Producto"); var channel=mock(Channel.class); var store=registro();
        procesador(true).procesar(Dominio.DESPACHO,mensaje(e),channel,x -> assertThat(store.guardar(Dominio.DESPACHO,x)).isTrue());
        verify(channel).basicAck(42,false); verifyNoMoreInteractions(channel);
        assertThat(registro().guardar(Dominio.DESPACHO,e)).isFalse();
        assertThat(json.readValue(Files.readAllBytes(temporal.resolve("despacho/"+e.eventId()+".json")),EventoPedido.class)).isEqualTo(e);
    }
    @Test void dosReintentosYExitoConfirmanUnaVez() throws Exception {
        var channel=mock(Channel.class); var count=new AtomicInteger();
        procesador(true).procesar(Dominio.DESPACHO,mensaje(evento("Producto")),channel,e -> {
            if(count.incrementAndGet()<3) throw new FalloRecuperable("temporal");
        });
        verify(pause,times(2)).await(Duration.ofSeconds(3));
        assertThat(count.get()).isEqualTo(3); verify(channel).basicAck(42,false); verifyNoMoreInteractions(channel);
    }
    @Test void agotamientoRechazaSinReencolarTrasTresIntentos() throws Exception {
        var channel=mock(Channel.class); var count=new AtomicInteger();
        procesador(true).procesar(Dominio.DESPACHO,mensaje(evento("Producto")),channel,e -> {
            count.incrementAndGet(); throw new FalloRecuperable("temporal");
        });
        assertThat(count.get()).isEqualTo(3); verify(channel).basicNack(42,false,false); verifyNoMoreInteractions(channel);
    }
    @Test void falloPermanenteNoReintenta() throws Exception {
        var channel=mock(Channel.class); var count=new AtomicInteger();
        procesador(true).procesar(Dominio.DESPACHO,mensaje(evento("Producto")),channel,e -> {
            count.incrementAndGet(); throw new FalloPermanente("conflicto");
        });
        assertThat(count.get()).isOne(); verify(channel).basicNack(42,false,false); verifyNoMoreInteractions(channel);
    }
    @Test void jsonMalformadoNuloYDtoInvalidoNoLleganAlNegocio() throws Exception {
        for(byte[] bytes:new byte[][]{"{".getBytes(),"null".getBytes(),"{}".getBytes(),json.writeValueAsBytes(evento(""))}) {
            var channel=mock(Channel.class);
            procesador(true).procesar(Dominio.DESPACHO,mensaje(bytes),channel,e -> fail("No debe ejecutar negocio"));
            verify(channel).basicNack(42,false,false); verifyNoMoreInteractions(channel);
        }
    }
    @Test void notificacionNoAdmiteCreacion() throws Exception {
        var channel=mock(Channel.class);
        procesador(true).procesar(Dominio.NOTIFICACIONES,mensaje(evento("Producto")),channel,e -> fail("Tipo incorrecto"));
        verify(channel).basicNack(42,false,false); verifyNoMoreInteractions(channel);
    }
    @Test void marcaDemoRechazaSinEjecutarNegocio() throws Exception {
        var channel=mock(Channel.class);
        procesador(true).procesar(Dominio.DESPACHO,mensaje(evento("Producto [ERROR-DLQ]")),channel,e -> fail("Demo debe fallar"));
        verify(channel).basicNack(42,false,false); verifyNoMoreInteractions(channel);
    }
    @Test void demoPuedeDesactivarse() throws Exception {
        var channel=mock(Channel.class); var count=new AtomicInteger();
        procesador(false).procesar(Dominio.DESPACHO,mensaje(evento("Producto [ERROR-DLQ]")),channel,e -> count.incrementAndGet());
        assertThat(count.get()).isOne(); verify(channel).basicAck(42,false);
    }
    @Test void falloDelAckNoReejecutaNegocioNiEnviaNack() throws Exception {
        var channel=mock(Channel.class); var count=new AtomicInteger();
        doThrow(new IOException("canal cerrado")).when(channel).basicAck(42,false);
        assertThatThrownBy(() -> procesador(true).procesar(Dominio.DESPACHO,mensaje(evento("Producto")),channel,e -> count.incrementAndGet()))
            .isInstanceOf(IOException.class);
        assertThat(count.get()).isOne(); verify(channel).basicAck(42,false); verifyNoMoreInteractions(channel);
    }
    @Test void falloDelNackPropagaErrorDeCanal() throws Exception {
        var channel=mock(Channel.class);
        doThrow(new IOException("canal cerrado")).when(channel).basicNack(42,false,false);
        assertThatThrownBy(() -> procesador(true).procesar(Dominio.DESPACHO,mensaje("{".getBytes()),channel,e -> fail("JSON invalido")))
            .isInstanceOf(IOException.class);
        verify(channel).basicNack(42,false,false); verifyNoMoreInteractions(channel);
    }
    @Test void registroSeparaDominiosYDetectaIdConContenidoDistinto() {
        var e=evento("Producto"); var store=registro();
        assertThat(store.guardar(Dominio.DESPACHO,e)).isTrue();
        assertThat(store.guardar(Dominio.AUDITORIA,e)).isTrue();
        var cambiado=new EventoPedido(e.eventId(),EventoPedido.Tipo.ELIMINADO,e.ocurridoEn(),e.pedido());
        assertThatThrownBy(() -> store.guardar(Dominio.DESPACHO,cambiado)).isInstanceOf(FalloPermanente.class);
    }
    @Test void falloDeDiscoEsRecuperable() throws Exception {
        Files.writeString(temporal.resolve("despacho"),"impide crear directorio");
        assertThatThrownBy(() -> registro().guardar(Dominio.DESPACHO,evento("Producto"))).isInstanceOf(FalloRecuperable.class);
    }
    @Test void listenersEjecutanSusTresServiciosConElDominioCorrecto() throws Exception {
        var e=evento("Producto"); var cambio=new EventoPedido(e.eventId(),EventoPedido.Tipo.ESTADO_ACTUALIZADO,e.ocurridoEn(),e.pedido());
        var channel=mock(Channel.class); var store=registro(); var processor=procesador(true);
        new cl.duoc.worlddelivery_operaciones.despacho.DespachoListener(processor,
            new cl.duoc.worlddelivery_operaciones.despacho.DespachoService(store)).recibir(mensaje(e),channel);
        new cl.duoc.worlddelivery_operaciones.notificaciones.NotificacionesListener(processor,
            new cl.duoc.worlddelivery_operaciones.notificaciones.NotificacionesService(store)).recibir(mensaje(cambio),channel);
        new cl.duoc.worlddelivery_operaciones.auditoria.AuditoriaListener(processor,
            new cl.duoc.worlddelivery_operaciones.auditoria.AuditoriaService(store)).recibir(mensaje(e),channel);
        verify(channel,times(3)).basicAck(42,false); verifyNoMoreInteractions(channel);
        for(Dominio dominio:Dominio.values()) assertThat(temporal.resolve(dominio.carpeta()+"/"+e.eventId()+".json")).exists();
    }
    @Test void aceptaContratoJsonConFechasIsoYEstadoConEspacios() throws Exception {
        String payload="""
            {"eventId":"a13e8700-6789-4cde-8567-123456789abc","tipo":"ESTADO_ACTUALIZADO",
             "ocurridoEn":"2026-11-02T12:00:00Z","pedido":{"id":1,"producto":"Demo",
             "cliente":"Cliente","direccion":"Direccion","fechaEntrega":"2026-11-03","estado":"EN TRANSITO"}}
            """;
        var channel=mock(Channel.class); var count=new AtomicInteger();
        procesador(true).procesar(Dominio.NOTIFICACIONES,mensaje(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)),channel,e -> {
            count.incrementAndGet(); assertThat(e.pedido().estado()).isEqualTo(EventoPedido.Estado.EN_TRANSITO);
        });
        assertThat(count.get()).isOne(); verify(channel).basicAck(42,false); verifyNoMoreInteractions(channel);
    }

    @Test void modifiedEventReachesAuditAndOldSnapshotsRemainReadable() throws Exception {
        var original=evento("Producto");
        var modified=new EventoPedido(original.eventId(),EventoPedido.Tipo.MODIFICADO,original.ocurridoEn(),original.pedido());
        var channel=mock(Channel.class);
        var store=registro();
        procesador(false).procesar(Dominio.AUDITORIA,mensaje(modified),channel,e -> store.guardar(Dominio.AUDITORIA,e));
        verify(channel).basicAck(42,false);
        assertThat(Dominio.NOTIFICACIONES.acepta(modified.tipo())).isFalse();
        assertThat(json.readValue(json.writeValueAsBytes(original),EventoPedido.class)).isEqualTo(original);
    }
}
