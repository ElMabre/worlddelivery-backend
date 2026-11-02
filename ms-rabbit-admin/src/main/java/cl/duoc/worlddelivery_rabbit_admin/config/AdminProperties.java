package cl.duoc.worlddelivery_rabbit_admin.config;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated
@ConfigurationProperties("app.admin")
public record AdminProperties(@NotBlank String scope, @Min(1) @Max(7) int replicas,
                              @Min(1) int maxLength) {}
