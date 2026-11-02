package cl.duoc.worlddelivery_rabbit_admin;
import cl.duoc.worlddelivery_rabbit_admin.config.SecurityConfig;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.AmqpIOException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.Instant;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"app.security.issuer=https://issuer.example.test", "app.security.client-id=client-test",
    "spring.rabbitmq.username=test", "spring.rabbitmq.password=test", "app.cors.allowed-origins=https://front.example.test"})
@AutoConfigureMockMvc
class AdminTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean RabbitAdmin broker;
    RequestPostProcessor admin() { return jwt().authorities(new SimpleGrantedAuthority("SCOPE_resource-server-worlddelivery/admin")); }
    @Test void todasLasMutacionesRequierenTokenYScopeAdmin() throws Exception {
        var requests=java.util.List.of(post("/api/rabbitmq/queues"),delete("/api/rabbitmq/queues/demo"),
            post("/api/rabbitmq/exchanges"),delete("/api/rabbitmq/exchanges/demo"),
            post("/api/rabbitmq/bindings"),delete("/api/rabbitmq/bindings"),get("/api/rabbitmq/queues/demo"));
        for(var request:requests) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_resource-server-worlddelivery/delete"))))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(broker);
    }
    @Test void tokenInvalidoDevuelve401SinDetalles() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("detalle privado"));
        mvc.perform(get("/api/rabbitmq/queues/demo").header("Authorization","Bearer invalid"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(broker);
    }
    @Test void creaColaQuorumDurableConConfiguracionCentral() throws Exception {
        when(broker.declareQueue(any(Queue.class))).thenReturn("demo.queue");
        mvc.perform(post("/api/rabbitmq/queues").with(admin()).contentType("application/json").content("{\"name\":\"demo.queue\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("demo.queue"));
        var capture=ArgumentCaptor.forClass(Queue.class);verify(broker).declareQueue(capture.capture());
        var queue=capture.getValue(); assertThat(queue.isDurable()).isTrue();assertThat(queue.isExclusive()).isFalse();assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.getArguments()).containsEntry("x-queue-type","quorum").containsEntry("x-quorum-initial-group-size",2)
            .containsEntry("x-max-length",10000).containsEntry("x-overflow","reject-publish");
    }
    @Test void validaDtoNombresReservadosYPropiedadesNoAdmitidas() throws Exception {
        for(String body:new String[]{"{}","{\"name\":\"\"}","{\"name\":\"amq.demo\"}","{\"name\":\"a/b\"}",
            "{\"name\":\"demo\",\"durable\":false}","{"}) {
            mvc.perform(post("/api/rabbitmq/queues").with(admin()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        }
        verifyNoInteractions(broker);
    }
    @Test void creaTresTiposDeExchange() throws Exception {
        for(String type:new String[]{"DIRECT","TOPIC","FANOUT"}) {
            mvc.perform(post("/api/rabbitmq/exchanges").with(admin()).contentType("application/json")
                .content("{\"name\":\"demo.exchange\",\"type\":\""+type+"\"}"))
                .andExpect(status().isCreated());
        }
        var capture=ArgumentCaptor.forClass(Exchange.class);verify(broker,times(3)).declareExchange(capture.capture());
        assertThat(capture.getAllValues()).extracting(Exchange::getType).containsExactly("direct","topic","fanout");
        assertThat(capture.getAllValues()).allMatch(e -> e.isDurable() && !e.isAutoDelete());
    }
    @Test void rechazaTipoExchangeInvalido() throws Exception {
        for(String body:new String[]{"{\"name\":\"demo\"}","{\"name\":\"demo\",\"type\":\"INVALID\"}"})
            mvc.perform(post("/api/rabbitmq/exchanges").with(admin()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(broker);
    }
    @Test void creaYEliminaMismoBindingSinConfundirComodines() throws Exception {
        mvc.perform(post("/api/rabbitmq/bindings").with(admin()).contentType("application/json")
            .content("{\"queue\":\"demo.queue\",\"exchange\":\"demo.exchange\",\"routingKey\":\"pedido.#\"}"))
            .andExpect(status().isCreated());
        mvc.perform(delete("/api/rabbitmq/bindings").with(admin()).param("queue","demo.queue").param("exchange","demo.exchange").param("routingKey","pedido.#"))
            .andExpect(status().isNoContent());
        var created=ArgumentCaptor.forClass(Binding.class);var removed=ArgumentCaptor.forClass(Binding.class);
        verify(broker).declareBinding(created.capture());verify(broker).removeBinding(removed.capture());
        assertThat(created.getValue().getDestination()).isEqualTo("demo.queue");
        assertThat(created.getValue().getExchange()).isEqualTo("demo.exchange");
        assertThat(created.getValue().getRoutingKey()).isEqualTo("pedido.#");
        assertThat(removed.getValue().getDestination()).isEqualTo(created.getValue().getDestination());
        assertThat(removed.getValue().getExchange()).isEqualTo(created.getValue().getExchange());
        assertThat(removed.getValue().getRoutingKey()).isEqualTo(created.getValue().getRoutingKey());
        assertThat(removed.getValue().getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE);
        assertThat(removed.getValue().getArguments()).isEmpty();
    }
    @Test void validaBindingEnCreacionYBorrado() throws Exception {
        mvc.perform(post("/api/rabbitmq/bindings").with(admin()).contentType("application/json")
            .content("{\"queue\":\"demo\",\"exchange\":\"demo\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/rabbitmq/bindings").with(admin()).param("queue","demo").param("exchange","demo").param("routingKey","x".repeat(129)))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/rabbitmq/queues/amq.demo").with(admin())).andExpect(status().isBadRequest());
        verifyNoInteractions(broker);
    }
    @Test void eliminaColaYExchangeConfirmados() throws Exception {
        when(broker.deleteQueue("demo.queue")).thenReturn(true);when(broker.deleteExchange("demo.exchange")).thenReturn(true);
        mvc.perform(delete("/api/rabbitmq/queues/demo.queue").with(admin())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/rabbitmq/exchanges/demo.exchange").with(admin())).andExpect(status().isNoContent());
        verify(broker).deleteQueue("demo.queue");verify(broker).deleteExchange("demo.exchange");
    }
    @Test void noInventaExitoSiBrokerNoConfirma() throws Exception {
        mvc.perform(delete("/api/rabbitmq/queues/demo").with(admin())).andExpect(status().isServiceUnavailable());
        mvc.perform(delete("/api/rabbitmq/exchanges/demo").with(admin())).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/rabbitmq/queues").with(admin()).contentType("application/json").content("{\"name\":\"demo\"}"))
            .andExpect(status().isServiceUnavailable());
    }
    @Test void consultaConteosRealesSinInventarListado() throws Exception {
        var props=new Properties();props.put(RabbitAdmin.QUEUE_MESSAGE_COUNT,2);props.put(RabbitAdmin.QUEUE_CONSUMER_COUNT,1);
        when(broker.getQueueProperties("demo")).thenReturn(props);
        mvc.perform(get("/api/rabbitmq/queues/demo").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.messagesReady").value(2)).andExpect(jsonPath("$.consumers").value(1));
        mvc.perform(get("/api/rabbitmq/queues/missing").with(admin())).andExpect(status().isServiceUnavailable());
    }
    @Test void traduceConflictoYRecursoAusenteDelBroker() throws Exception {
        for(int code:new int[]{404,406}) {
            var close=new AMQP.Channel.Close.Builder().replyCode(code).replyText("detalle privado").classId(0).methodId(0).build();
            doThrow(new AmqpIOException(new java.io.IOException(new ShutdownSignalException(false,false,close,null)))).when(broker).declareExchange(any());
            mvc.perform(post("/api/rabbitmq/exchanges").with(admin()).contentType("application/json")
                .content("{\"name\":\"demo\",\"type\":\"DIRECT\"}"))
                .andExpect(status().is(code==404 ? 404:409)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalle privado"))));
        }
    }
    @Test void desconexionDevuelve503SinFiltrarDetalles() throws Exception {
        doThrow(new AmqpIOException(new java.io.IOException("password=privado"))).when(broker).declareBinding(any());
        mvc.perform(post("/api/rabbitmq/bindings").with(admin()).contentType("application/json")
            .content("{\"queue\":\"demo\",\"exchange\":\"demo\",\"routingKey\":\"\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("privado"))));
    }
    @Test void corsSoloPermiteOrigenConfigurado() throws Exception {
        mvc.perform(options("/api/rabbitmq/queues/demo").header("Origin","https://front.example.test").header("Access-Control-Request-Method","DELETE")
            .header("Access-Control-Request-Headers","Authorization"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://front.example.test"));
        mvc.perform(options("/api/rabbitmq/queues/demo").header("Origin","https://otro.example.test").header("Access-Control-Request-Method","DELETE"))
            .andExpect(status().isForbidden());
    }
    Jwt token(String issuer,String use,String client,Instant expiration) {
        var builder=Jwt.withTokenValue("local").header("alg","RS256").issuer(issuer).claim("token_use",use).claim("client_id",client)
            .issuedAt(Instant.now().minusSeconds(3600));
        if(expiration!=null) builder.expiresAt(expiration);
        return builder.build();
    }
    @Test void validaIssuerExpiracionTokenUseYCliente() {
        var validator=SecurityConfig.validators("https://issuer.example.test","client-test");var future=Instant.now().plusSeconds(300);
        assertThat(validator.validate(token("https://issuer.example.test","access","client-test",future)).hasErrors()).isFalse();
        for(var invalid:java.util.List.of(token("https://otro.example.test","access","client-test",future),
            token("https://issuer.example.test","id","client-test",future),token("https://issuer.example.test","access","otro",future),
            token("https://issuer.example.test","access","client-test",Instant.now().minusSeconds(120)),
            token("https://issuer.example.test","access","client-test",null)))
            assertThat(validator.validate(invalid).hasErrors()).isTrue();
    }
}
