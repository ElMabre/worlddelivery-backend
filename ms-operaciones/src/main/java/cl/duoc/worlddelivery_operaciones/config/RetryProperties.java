package cl.duoc.worlddelivery_operaciones.config;

import java.time.Duration;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("spring.rabbitmq.listener.simple.retry")
public record RetryProperties(boolean enabled, @Min(1) @Max(3) int maxAttempts,
                              @NotNull Duration initialInterval) {
    @AssertTrue(message="El intervalo de reintento debe ser positivo")
    public boolean isIntervalValid() { return initialInterval != null && initialInterval.toMillis() > 0; }
    public int attempts() { return enabled ? maxAttempts : 1; }
}
