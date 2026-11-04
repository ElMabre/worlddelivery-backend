package cl.duoc.worlddelivery_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test")
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
@AutoConfigureMockMvc
class WorlddeliveryBackendApplicationTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean cl.duoc.worlddelivery_backend.messaging.PedidoEventProducer producer;
    @Autowired cl.duoc.worlddelivery_backend.repository.PedidoRepository pedidos;
    @Autowired cl.duoc.worlddelivery_backend.messaging.EventoPendienteRepository pendientes;
    @Autowired cl.duoc.worlddelivery_backend.service.PedidoService service;
    @Autowired cl.duoc.worlddelivery_backend.messaging.PublicadorPendientes relay;
    @Autowired cl.duoc.worlddelivery_backend.config.DatosIniciales seed;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private cl.duoc.worlddelivery_backend.dto.CrearPedidoRequest pedidoDemo() {
        return new cl.duoc.worlddelivery_backend.dto.CrearPedidoRequest("[ERROR-DLQ] Prueba", "Cliente", "Dirección",
            java.time.LocalDate.of(2026,12,1), cl.duoc.worlddelivery_backend.domain.EstadoPedido.PENDIENTE);
    }


    private SimpleGrantedAuthority scope(String name) {
        return new SimpleGrantedAuthority("SCOPE_resource-server-worlddelivery/" + name);
    }

    @Test void rejectsRequestsWithoutToken() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test void rejectsInvalidBearerToken() throws Exception {
        when(decoder.decode("invalid-local-token")).thenThrow(new BadJwtException("Invalid test token"));
        mvc.perform(get("/api/pedidos").header("Authorization", "Bearer invalid-local-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test void rejectsTokenWithoutReadScope() throws Exception {
        mvc.perform(get("/api/pedidos").with(jwt().authorities(scope("write"))))
            .andExpect(status().isForbidden());
    }

    @Test void listsInitialOrdersWithReadScope() throws Exception {
        mvc.perform(get("/api/pedidos").with(jwt().authorities(scope("api-access"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].producto").value("Notebook HP Envy"));
    }

    @Test void readScopeCannotMutateOrders() throws Exception {
        mvc.perform(post("/api/pedidos").with(jwt().authorities(scope("api-access")))
                .contentType("application/json").content("{\"producto\":\"Demo\",\"cliente\":\"Demo\",\"direccion\":\"Demo\",\"fechaEntrega\":\"2026-12-01\",\"estado\":\"PENDIENTE\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/pedidos/1/estado").with(jwt().authorities(scope("api-access")))
                .contentType("application/json").content("{\"estado\":\"ENTREGADO\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/pedidos/1").with(jwt().authorities(scope("api-access"))))
            .andExpect(status().isForbidden());
    }

    @Test void createsUpdatesAndDeletesOrderWithRequiredScopes() throws Exception {
        var response = mvc.perform(post("/api/pedidos").with(jwt().authorities(scope("write")))
                .contentType("application/json").content("""
                {"producto":"Prueba local","cliente":"Demo","direccion":"Dirección de prueba",
                 "fechaEntrega":"2026-12-01","estado":"EN PREPARACION"}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.producto").value("Prueba local"))
            .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        mvc.perform(patch("/api/pedidos/" + id + "/estado").with(jwt().authorities(scope("update")))
                .contentType("application/json").content("{\"estado\":\"ENTREGADO\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENTREGADO"));
        mvc.perform(delete("/api/pedidos/" + id).with(jwt().authorities(scope("delete"))))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/api/pedidos/" + id).with(jwt().authorities(scope("delete"))))
            .andExpect(status().isNotFound());
    }

    @Test void updateMissingOrderReturns404() throws Exception {
        mvc.perform(patch("/api/pedidos/999999/estado").with(jwt().authorities(scope("update")))
                .contentType("application/json").content("{\"estado\":\"ENTREGADO\"}"))
            .andExpect(status().isNotFound());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={
        "{}",
        "{\"producto\": \"   \", \"cliente\": \"Demo\", \"direccion\": \"Demo\", \"fechaEntrega\": \"2026-12-01\", \"estado\": \"PENDIENTE\"}",
        "{\"producto\":\"Demo\",\"cliente\":\"Demo\",\"direccion\":\"Demo\",\"fechaEntrega\":\"incorrecta\",\"estado\":\"PENDIENTE\"}",
        "{\"producto\":\"Demo\",\"cliente\":\"Demo\",\"direccion\":\"Demo\",\"fechaEntrega\":\"2026-12-01\",\"estado\":\"INVENTADO\"}"
    })
    void rejectsInvalidInputWithoutSavingOrderOrEvent(String body) throws Exception {
        long before=pedidos.count(); long events=pendientes.count();
        mvc.perform(post("/api/pedidos").with(jwt().authorities(scope("write")))
                .contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        org.assertj.core.api.Assertions.assertThat(pedidos.count()).isEqualTo(before);
        org.assertj.core.api.Assertions.assertThat(pendientes.count()).isEqualTo(events);
    }

    @Test void persistsOrderAndBothCreationDestinations() {
        long before=pendientes.count();
        var response=service.crear(pedidoDemo());
        entityManager.flush(); entityManager.clear();
        var stored=pedidos.findById(response.id()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getProducto()).isEqualTo("[ERROR-DLQ] Prueba");
        var events=pendientes.findAll().stream().filter(e -> e.getPayload().contains("[ERROR-DLQ]")).toList();
        org.assertj.core.api.Assertions.assertThat(pendientes.count()).isEqualTo(before+2);
        org.assertj.core.api.Assertions.assertThat(events).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(events).extracting(e -> e.getExchangeName())
            .containsExactlyInAnyOrder("wd.pedidos.direct","wd.eventos.topic");
        org.assertj.core.api.Assertions.assertThat(events).extracting(e -> e.getRoutingKey()).containsOnly("pedido.creado");
        org.assertj.core.api.Assertions.assertThat(events.get(0).getEventId()).isEqualTo(events.get(1).getEventId());
    }

    @Test void stateAndDeletionEventsContainOrderSnapshot() {
        var response=service.crear(pedidoDemo()); pendientes.deleteAll();
        service.actualizar(response.id(),new cl.duoc.worlddelivery_backend.dto.ActualizarEstadoRequest(
            cl.duoc.worlddelivery_backend.domain.EstadoPedido.EN_REPARTO));
        service.eliminar(response.id()); entityManager.flush(); entityManager.clear();
        org.assertj.core.api.Assertions.assertThat(pedidos.findById(response.id())).isEmpty();
        var events=pendientes.findTop50ByOrderByIdAsc();
        org.assertj.core.api.Assertions.assertThat(events).extracting(e -> e.getRoutingKey())
            .containsExactly("pedido.estado.en_reparto","pedido.eliminado");
        org.assertj.core.api.Assertions.assertThat(events.get(1).getPayload()).contains("EN REPARTO","[ERROR-DLQ]");
    }

    @Test void seedDoesNotDuplicateExistingData() throws Exception {
        long before=pedidos.count(); seed.run();
        org.assertj.core.api.Assertions.assertThat(pedidos.count()).isEqualTo(before);
    }

    @Test void brokerFailureKeepsPendingEventAndRecoveryRemovesIt() {
        pendientes.deleteAll(); service.crear(pedidoDemo());
        org.mockito.Mockito.doThrow(new IllegalStateException("Broker unavailable"))
            .when(producer).publicar(org.mockito.ArgumentMatchers.any());
        relay.publicarLote();
        org.assertj.core.api.Assertions.assertThat(pendientes.count()).isEqualTo(2);
        org.mockito.Mockito.reset(producer);
        relay.publicarLote();
        org.assertj.core.api.Assertions.assertThat(pendientes.count()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PATCH","DELETE"})
    void corsAllowsConfiguredOriginAndMethod(String method) throws Exception {
        mvc.perform(options("/api/pedidos/1/estado").header("Origin","https://frontend.example.test")
                .header("Access-Control-Request-Method",method)
                .header("Access-Control-Request-Headers","authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin","https://frontend.example.test"));
    }

    @Test void corsRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/pedidos").header("Origin","https://unknown.example.test")
                .header("Access-Control-Request-Method","POST"))
            .andExpect(status().isForbidden());
    }

    @Test void rollbackRemovesBothOrderAndPendingEvents() {
        long ordersBefore=pedidos.count(); long eventsBefore=pendientes.count();
        var response=service.crear(pedidoDemo());
        entityManager.flush();
        org.springframework.test.context.transaction.TestTransaction.flagForRollback();
        org.springframework.test.context.transaction.TestTransaction.end();
        org.assertj.core.api.Assertions.assertThat(pedidos.findById(response.id())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(pedidos.count()).isEqualTo(ordersBefore);
        org.assertj.core.api.Assertions.assertThat(pendientes.count()).isEqualTo(eventsBefore);
    }
}
