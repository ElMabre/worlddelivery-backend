package cl.duoc.worlddelivery_operaciones.config;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated
@ConfigurationProperties(prefix="app.processing")
public record ProcesamientoProperties(@Min(0) @Max(2) int maxRetries,
    boolean demoFailureEnabled, @NotBlank String storageDirectory) {}
