package cl.duoc.worlddelivery_backend.config;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated
@ConfigurationProperties(prefix="app.messaging")
public record MensajeriaProperties(@NotBlank String directExchange, @NotBlank String topicExchange,
    @NotBlank String createdKey, @NotBlank String deletedKey, @NotBlank String statePrefix) {}
