package cl.duoc.worlddelivery_usuarios.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/usuarios", "/api/usuarios/**")
                .access((authentication, context) -> {
                    var auth = authentication.get();
                    if (!(auth instanceof JwtAuthenticationToken token)) return new AuthorizationDecision(false);
                    var groups = token.getToken().getClaimAsStringList("cognito:groups");
                    boolean scope = auth.getAuthorities().stream().anyMatch(
                        authority -> authority.getAuthority().equals("SCOPE_resource-server-worlddelivery/admin"));
                    return new AuthorizationDecision(scope && groups != null && groups.contains("Admin"));
                }).anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())).build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.cognito.region}") String region,
                          @Value("${app.cognito.user-pool-id}") String pool,
                          @Value("${app.cognito.client-id}") String client) {
        String issuer = "https://cognito-idp." + region + ".amazonaws.com/" + pool;
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(validators(issuer, client));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validators(String issuer, String client) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), jwt -> {
            if (jwt.getExpiresAt() != null && "access".equals(jwt.getClaimAsString("token_use"))
                && client.equals(jwt.getClaimAsString("client_id"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        });
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        var allowed = new java.util.ArrayList<>(List.of("https://54.156.80.92", "http://localhost:4200"));
        origins.stream().map(String::trim).filter(s -> !s.isBlank() && !allowed.contains(s)).forEach(allowed::add);
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowed);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
