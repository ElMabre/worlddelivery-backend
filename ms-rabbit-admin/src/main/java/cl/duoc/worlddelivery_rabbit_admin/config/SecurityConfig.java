package cl.duoc.worlddelivery_rabbit_admin.config;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,AdminProperties config) throws Exception {
        return http.csrf(c -> c.disable()).cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/rabbitmq/**").hasAuthority("SCOPE_"+config.scope()).anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint((req,res,ex) -> {
                res.setStatus(401); res.setHeader("WWW-Authenticate","Bearer"); res.setContentType("application/json");
                res.getWriter().write("{\"message\":\"Token ausente, invalido o vencido\"}");
            }))
            .exceptionHandling(e -> e.accessDeniedHandler((req,res,ex) -> {
                res.setStatus(403); res.setContentType("application/json");
                res.getWriter().write("{\"message\":\"Se requiere el scope de administrador\"}");
            })).build();
    }
    @Bean JwtDecoder jwtDecoder(@Value("${app.security.issuer}") String issuer,
                               @Value("${app.security.client-id}") String clientId) {
        if (issuer.isBlank() || clientId.isBlank()) throw new IllegalArgumentException("Configurar issuer y client ID");
        var decoder=NimbusJwtDecoder.withJwkSetUri(issuer+"/.well-known/jwks.json").build();
        decoder.setJwtValidator(validators(issuer,clientId));
        return decoder;
    }
    public static OAuth2TokenValidator<Jwt> validators(String issuer,String clientId) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), jwt -> {
            if (jwt.getExpiresAt()!=null && "access".equals(jwt.getClaimAsString("token_use")) && clientId.equals(jwt.getClaimAsString("client_id")))
                return OAuth2TokenValidatorResult.success();
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Se requiere access token del cliente configurado",null));
        });
    }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins) {
        var config=new CorsConfiguration();
        config.setAllowedOrigins(origins.stream().map(String::trim).filter(s -> !s.isBlank()).toList());
        config.setAllowedMethods(List.of("GET","POST","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type"));
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**",config); return source;
    }
}
