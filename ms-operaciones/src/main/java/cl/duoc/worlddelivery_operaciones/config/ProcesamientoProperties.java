package cl.duoc.worlddelivery_operaciones.config;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated
@ConfigurationProperties(prefix="app.processing")
public record ProcesamientoProperties(boolean demoFailureEnabled, @NotBlank String storageDirectory) {}
