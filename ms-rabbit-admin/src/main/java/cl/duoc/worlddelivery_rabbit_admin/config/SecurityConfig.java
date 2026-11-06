package cl.duoc.worlddelivery_rabbit_admin.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AdminProperties config) throws Exception {
        return http
            .csrf(c -> c.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/rabbitmq/**")
                .hasAnyAuthority("SCOPE_" + config.scope(), "Admin", "ROLE_Admin")
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setHeader("WWW-Authenticate", "Bearer");
                    res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Token ausente, invalido o vencido\"}");
                })
            )
            .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> {
                res.setStatus(403);
                res.setContentType("application/json");
                res.getWriter().write("{\"message\":\"Se requiere el scope o rol de administrador\"}");
            }))
            .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var scopesConverter = new JwtGrantedAuthoritiesConverter();
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new ArrayList<GrantedAuthority>(scopesConverter.convert(jwt));
            var groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups != null) {
                for (String group : groups) {
                    if (group != null && !group.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(group));
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + group));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.issuer}") String issuer,
                          @Value("${app.security.client-id}") String clientId) {
        if (issuer == null || issuer.isBlank() || clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Configurar issuer y client ID");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(validators(issuer, clientId));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validators(String issuer, String clientId) {
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            jwt -> {
                if (jwt.getExpiresAt() != null && "access".equals(jwt.getClaimAsString("token_use")) && clientId.equals(jwt.getClaimAsString("client_id"))) {
                    return OAuth2TokenValidatorResult.success();
                }
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Se requiere access token del cliente configurado", null));
            }
        );
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        var allowed = new ArrayList<>(List.of("https://54.156.80.92", "http://localhost:4200"));
        if (origins != null) {
            origins.stream().map(String::trim).filter(s -> !s.isBlank() && !allowed.contains(s)).forEach(allowed::add);
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowed);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
