package cl.duoc.worlddelivery_usuarios.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import cl.duoc.worlddelivery_usuarios.api.UsuarioResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

@Service
public class UsuariosService {
    private final CognitoIdentityProviderClient cognito;
    private final String pool;

    public UsuariosService(CognitoIdentityProviderClient cognito,
                           @Value("${app.cognito.user-pool-id}") String pool) {
        this.cognito = cognito;
        this.pool = pool;
    }

    public List<UsuarioResponse> listar() {
        var users = new ArrayList<UsuarioResponse>();
        String token = null;
        do {
            var page = cognito.listUsers(ListUsersRequest.builder()
                .userPoolId(pool).limit(60).paginationToken(token).build());
            for (var user : page.users()) {
                users.add(response(user.username(), Boolean.TRUE.equals(user.enabled()),
                    user.userStatusAsString(), user.attributes(), grupos(user.username())));
            }
            token = page.paginationToken();
        } while (token != null && !token.isBlank());
        return users;
    }

    private List<String> grupos(String username) {
        var groups = new ArrayList<String>();
        String token = null;
        do {
            var page = cognito.adminListGroupsForUser(AdminListGroupsForUserRequest.builder()
                .userPoolId(pool).username(username).limit(60).nextToken(token).build());
            page.groups().forEach(group -> groups.add(group.groupName()));
            token = page.nextToken();
        } while (token != null && !token.isBlank());
        return groups;
    }

    public synchronized void deshabilitar(String username, String actor) {
        var target = cognito.adminGetUser(AdminGetUserRequest.builder().userPoolId(pool).username(username).build());
        if (target.username().equals(actor)) throw new ResponseStatusException(
            HttpStatus.CONFLICT, "No puedes deshabilitar tu propia cuenta.");
        cognito.adminDisableUser(AdminDisableUserRequest.builder().userPoolId(pool).username(username).build());
    }

    public synchronized UsuarioResponse cambiarRol(String username, String rol, String actor) {
        if (!Set.of("Admin", "Usuario").contains(rol)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var target = cognito.adminGetUser(AdminGetUserRequest.builder().userPoolId(pool).username(username).build());
        if (target.username().equals(actor) && !rol.equals("Admin")) throw new ResponseStatusException(
            HttpStatus.CONFLICT, "No puedes retirar tu propio rol Admin.");
        var before = grupos(username);
        String other = rol.equals("Admin") ? "Usuario" : "Admin";
        // Cognito no ofrece una transacción entre grupos. Repetir la petición converge al rol solicitado.
        if (!before.contains(rol)) {
            cognito.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                .userPoolId(pool).username(username).groupName(rol).build());
        }
        if (before.contains(other)) {
            cognito.adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest.builder()
                .userPoolId(pool).username(username).groupName(other).build());
        }
        var user = cognito.adminGetUser(AdminGetUserRequest.builder().userPoolId(pool).username(username).build());
        return response(user.username(), Boolean.TRUE.equals(user.enabled()), user.userStatusAsString(),
            user.userAttributes(), grupos(username));
    }

    private UsuarioResponse response(String username, boolean enabled, String status,
                                     List<AttributeType> attributes, List<String> groups) {
        var values = new LinkedHashMap<String, String>();
        attributes.forEach(attribute -> values.put(attribute.name(), attribute.value()));
        String role = groups.contains("Admin") ? "Admin" : groups.contains("Usuario") ? "Usuario" : "Sin rol";
        return new UsuarioResponse(username, enabled, status, values, groups, role);
    }
}
