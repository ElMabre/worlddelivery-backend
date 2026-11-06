package cl.duoc.worlddelivery_backend;

import java.time.LocalDate;
import java.util.List;
import cl.duoc.worlddelivery_backend.domain.*;
import cl.duoc.worlddelivery_backend.dto.*;
import cl.duoc.worlddelivery_backend.repository.PedidoRepository;
import cl.duoc.worlddelivery_backend.messaging.*;
import cl.duoc.worlddelivery_backend.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class EvolucionPedidosTests {
    @Autowired MockMvc mvc;
    @Autowired PedidoRepository pedidos;
    @Autowired EventoPendienteRepository pendientes;
    @Autowired PedidoService service;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PedidoEventProducer producer;

    CrearPedidoRequest datos(EstadoPedido estado) {
        return new CrearPedidoRequest("Caja", "Ana", "Calle 1", LocalDate.of(2026,12,1), estado);
    }
    SimpleGrantedAuthority scope(String scope) { return new SimpleGrantedAuthority("SCOPE_resource-server-worlddelivery/"+scope); }

    @Test void softDeleteRetainsRowAndRestrictsInactiveList() throws Exception {
        var pedido=service.crear(datos(EstadoPedido.PENDIENTE));
        service.eliminar(pedido.id()); em.flush(); em.clear();
        assertThat(pedidos.findById(pedido.id()).orElseThrow().isActivo()).isFalse();
        assertThat(service.listar()).extracting(PedidoResponse::id).doesNotContain(pedido.id());
        mvc.perform(get("/api/pedidos/inactivos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos/inactivos").with(jwt().jwt(j -> j.claim("token_use","access"))
            .authorities(scope("admin")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/inactivos").with(jwt().jwt(j -> j.claim("token_use","id")
            .claim("cognito:groups",List.of("Admin"))).authorities(scope("admin")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/inactivos").with(jwt().jwt(j -> j.claim("token_use","access")
            .claim("cognito:groups",List.of("Admin"))).authorities(scope("admin"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(pedido.id()));
        mvc.perform(put("/api/pedidos/"+pedido.id()).with(jwt().jwt(j -> j.claim("token_use","access").claim("cognito:groups",java.util.List.of("Admin"))).authorities(scope("update")))
            .contentType("application/json").content(body("PENDIENTE"))).andExpect(status().isNotFound());
    }
    String body(String estado) {
        return "{\"producto\":\"Otra caja\",\"cliente\":\"Luis\",\"direccion\":\"Calle 2\",\"fechaEntrega\":\"2026-12-02\",\"estado\":\""+estado+"\"}";
    }
    @Test void deliveredEditsRequireStateChangeAndPublishOutboxAtomically() throws Exception {
        var pedido=service.crear(datos(EstadoPedido.ENTREGADO)); pendientes.deleteAll();
        mvc.perform(put("/api/pedidos/"+pedido.id()).with(jwt().jwt(j -> j.claim("token_use","access").claim("cognito:groups",java.util.List.of("Admin"))).authorities(scope("update")))
            .contentType("application/json").content(body("ENTREGADO")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").exists());
        assertThat(pedidos.findById(pedido.id()).orElseThrow().getProducto()).isEqualTo("Caja");
        assertThat(pendientes.count()).isZero();
        mvc.perform(put("/api/pedidos/"+pedido.id()).with(jwt().jwt(j -> j.claim("token_use","access").claim("cognito:groups",java.util.List.of("Admin"))).authorities(scope("update")))
            .contentType("application/json").content(body("EN PREPARACION")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.producto").value("Otra caja"))
            .andExpect(jsonPath("$.estado").value("EN PREPARACION"));
        em.flush(); em.clear();
        assertThat(pedidos.findById(pedido.id()).orElseThrow().getCliente()).isEqualTo("Luis");
        assertThat(pendientes.findTop50ByOrderByIdAsc()).extracting(EventoPendiente::getRoutingKey)
            .containsExactly("pedido.modificado","pedido.estado.en_preparacion");
        assertThat(pendientes.findTop50ByOrderByIdAsc().get(0).getPayload()).contains("MODIFICADO","Otra caja");
        org.springframework.test.context.transaction.TestTransaction.flagForRollback();
        org.springframework.test.context.transaction.TestTransaction.end();
        assertThat(pedidos.findById(pedido.id())).isEmpty();
    }
    @Test void editRequiresUpdateScopeAndFullValidBody() throws Exception {
        mvc.perform(put("/api/pedidos/1").with(jwt().authorities(scope("api-access")))
            .contentType("application/json").content(body("PENDIENTE"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/1").with(jwt().jwt(j -> j.claim("token_use","access").claim("cognito:groups",java.util.List.of("Admin"))).authorities(scope("update")))
            .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    @Test void unchangedStatePublishesOnlyModifiedEvent() {
        var p=service.crear(datos(EstadoPedido.PENDIENTE)); pendientes.deleteAll();
        service.editar(p.id(),new CrearPedidoRequest("Modificado","Ana","Calle 1",LocalDate.of(2026,12,1),EstadoPedido.PENDIENTE));
        assertThat(pendientes.findAll()).extracting(EventoPendiente::getRoutingKey).containsExactly("pedido.modificado");
    }
    @Test void rolesProtectEditingAndSoftDelete() throws Exception {
        var pedido=service.crear(datos(EstadoPedido.PENDIENTE));
        for (String group : List.of("Usuario", "Admin")) {
            var auth=jwt().jwt(j -> j.claim("token_use","access").claim("cognito:groups",List.of(group)))
                .authorities(scope("update"),scope("delete"));
            mvc.perform(put("/api/pedidos/"+pedido.id()).with(auth)
                .contentType("application/json").content(body("PENDIENTE"))).andExpect(status().isOk());
            mvc.perform(patch("/api/pedidos/"+pedido.id()+"/estado").with(auth)
                .contentType("application/json").content("{\"estado\":\"PENDIENTE\"}")).andExpect(status().isOk());
            if (group.equals("Usuario")) {
                mvc.perform(delete("/api/pedidos/"+pedido.id()).with(auth)).andExpect(status().isForbidden());
                assertThat(pedidos.findById(pedido.id()).orElseThrow().isActivo()).isTrue();
            }
        }
        var noRole=jwt().jwt(j -> j.claim("token_use","access")).authorities(scope("update"),scope("delete"));
        mvc.perform(put("/api/pedidos/"+pedido.id()).with(noRole).contentType("application/json")
            .content(body("PENDIENTE"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/pedidos/"+pedido.id()).with(noRole)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/pedidos/"+pedido.id()).with(jwt().jwt(j -> j.claim("token_use","access")
            .claim("cognito:groups",List.of("Admin"))).authorities(scope("delete")))).andExpect(status().isNoContent());
        assertThat(pedidos.findById(pedido.id()).orElseThrow().isActivo()).isFalse();
    }
}
