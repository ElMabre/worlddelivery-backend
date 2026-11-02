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

// The decoder is replaced before context startup: no Cognito discovery or network calls.
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test")
@AutoConfigureMockMvc
class WorlddeliveryBackendApplicationTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

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
                .contentType("application/json").content("{}"))
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
}
