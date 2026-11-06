package cl.duoc.worlddelivery_backend.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("adminAccess")
public class AdminAccess {
    public boolean allowed(Authentication authentication) {
        return hasGroup(authentication, "Admin");
    }
    public boolean canEdit(Authentication authentication) {
        return hasGroup(authentication, "Admin") || hasGroup(authentication, "Usuario");
    }
    private boolean hasGroup(Authentication authentication, String group) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        var groups = jwt.getToken().getClaimAsStringList("cognito:groups");
        return "access".equals(jwt.getToken().getClaimAsString("token_use"))
            && groups != null && groups.contains(group);
    }
}
