package cl.duoc.worlddelivery_usuarios;

import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import cl.duoc.worlddelivery_usuarios.config.SecurityConfig;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.cognito.user-pool-id=us-east-1_test","app.cognito.region=us-east-1",
    "app.cognito.client-id=test-client","app.cors.allowed-origins=https://frontend.example.test"})
@AutoConfigureMockMvc
class UsuariosTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CognitoIdentityProviderClient cognito;

    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("username","owner").claim("cognito:groups",List.of("Admin")))
            .authorities(new SimpleGrantedAuthority("SCOPE_resource-server-worlddelivery/admin"));
    }
    @Test void protectsAllOperationsFromMissingTokenAndScopeOnly() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios").with(jwt().authorities(
            new SimpleGrantedAuthority("SCOPE_resource-server-worlddelivery/admin")))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/ana/deshabilitar").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/ana/rol").with(jwt()).contentType("application/json")
            .content("{\"rol\":\"Admin\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(cognito);
    }
    @Test void listsEveryPageAndEveryGroup() throws Exception {
        when(cognito.listUsers(any(ListUsersRequest.class))).thenReturn(
            ListUsersResponse.builder().users(UserType.builder().username("ana").enabled(true)
                .attributes(AttributeType.builder().name("email").value("ana@example.test").build()).build())
                .paginationToken("next").build(),
            ListUsersResponse.builder().users(UserType.builder().username("bob").enabled(false).build()).build());
        when(cognito.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class))).thenReturn(
            AdminListGroupsForUserResponse.builder().groups(GroupType.builder().groupName("Otro").build()).nextToken("next-group").build(),
            AdminListGroupsForUserResponse.builder().groups(GroupType.builder().groupName("Admin").build()).build(),
            AdminListGroupsForUserResponse.builder().groups(GroupType.builder().groupName("Usuario").build()).build());
        mvc.perform(get("/api/usuarios").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].rol").value("Admin"))
            .andExpect(jsonPath("$[0].atributos.email").value("ana@example.test"))
            .andExpect(jsonPath("$[1].enabled").value(false));
        verify(cognito).listUsers(argThat((ListUsersRequest r) -> "next".equals(r.paginationToken())));
        verify(cognito).adminListGroupsForUser(argThat((AdminListGroupsForUserRequest r) -> "next-group".equals(r.nextToken())));
    }
    void target(String username) {
        when(cognito.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(
            AdminGetUserResponse.builder().username(username).enabled(true).build());
    }
    @Test void disablesCanonicalUserAndPreventsSelfDisableViaAlias() throws Exception {
        target("ana");
        mvc.perform(patch("/api/usuarios/ana/deshabilitar").with(admin())).andExpect(status().isNoContent());
        verify(cognito).adminDisableUser(argThat((AdminDisableUserRequest r) -> r.username().equals("ana")));
        reset(cognito); target("owner");
        mvc.perform(patch("/api/usuarios/owner@example.test/deshabilitar").with(admin())).andExpect(status().isConflict());
        verify(cognito,never()).adminDisableUser(any(AdminDisableUserRequest.class));
    }
    @Test void roleReplacementPreservesUnrelatedGroupsAndCanBeRetried() throws Exception {
        target("ana");
        when(cognito.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class))).thenReturn(
            AdminListGroupsForUserResponse.builder().groups(
                GroupType.builder().groupName("Usuario").build(),GroupType.builder().groupName("Operador").build()).build(),
            AdminListGroupsForUserResponse.builder().groups(
                GroupType.builder().groupName("Admin").build(),GroupType.builder().groupName("Operador").build()).build());
        mvc.perform(patch("/api/usuarios/ana/rol").with(admin()).contentType("application/json")
            .content("{\"rol\":\"Admin\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("Admin"));
        verify(cognito).adminAddUserToGroup(argThat((AdminAddUserToGroupRequest r) -> r.groupName().equals("Admin")));
        verify(cognito).adminRemoveUserFromGroup(argThat((AdminRemoveUserFromGroupRequest r) -> r.groupName().equals("Usuario")));
        mvc.perform(patch("/api/usuarios/ana/rol").with(admin()).contentType("application/json")
            .content("{\"rol\":\"Admin\"}")).andExpect(status().isOk());
        verify(cognito,times(1)).adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    }
    @Test void rejectsInvalidRoleBeforeAwsAndReportsPartialFailure() throws Exception {
        mvc.perform(patch("/api/usuarios/ana/rol").with(admin()).contentType("application/json")
            .content("{\"rol\":\"SuperAdmin\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(cognito);
        target("ana");
        when(cognito.adminListGroupsForUser(any(AdminListGroupsForUserRequest.class))).thenReturn(
            AdminListGroupsForUserResponse.builder().groups(GroupType.builder().groupName("Admin").build()).build());
        when(cognito.adminRemoveUserFromGroup(any(AdminRemoveUserFromGroupRequest.class)))
            .thenThrow(CognitoIdentityProviderException.builder().statusCode(500).message("private").build());
        mvc.perform(patch("/api/usuarios/ana/rol").with(admin()).contentType("application/json")
            .content("{\"rol\":\"Usuario\"}")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("parcialmente")));
    }
    @Test void validatesIssuerClientTokenUseAndExpiry() {
        var validators=SecurityConfig.validators("https://issuer.example.test","test-client");
        var builder=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg","RS256")
            .issuer("https://issuer.example.test").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("token_use","access").claim("client_id","test-client");
        assertThat(validators.validate(builder.build()).hasErrors()).isFalse();
        assertThat(validators.validate(builder.claim("token_use","id").build()).hasErrors()).isTrue();
        assertThat(validators.validate(builder.claim("token_use","access").claim("client_id","wrong").build()).hasErrors()).isTrue();
    }
    @Test void permitsPreflight() throws Exception {
        mvc.perform(options("/api/usuarios/ana/rol").header("Origin","https://frontend.example.test")
            .header("Access-Control-Request-Method","PATCH").header("Access-Control-Request-Headers","authorization,content-type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://frontend.example.test"));
    }
}
