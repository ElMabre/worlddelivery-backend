package cl.duoc.worlddelivery_operaciones.config;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.stream.Stream;
@Validated
@ConfigurationProperties(prefix="app.topology")
public record TopologiaProperties(
    @NotBlank String directExchange, @NotBlank String topicExchange, @NotBlank String deadLetterExchange,
    @Min(1) @Max(7) int replicas, @Min(1) int maxLength,
    @NotNull @Valid Ruta despacho, @NotNull @Valid Ruta notificaciones, @NotNull @Valid Ruta auditoria
) {
    public record Ruta(@NotBlank String queue, @NotBlank String deadLetterQueue,
                       @NotBlank String routingKey, @NotBlank String deadLetterKey) {}
    @AssertTrue(message="Exchanges, colas y claves DLQ deben ser únicos dentro de su categoría")
    public boolean isNamesUnique() {
        if (despacho==null || notificaciones==null || auditoria==null) return true;
        return Stream.of(directExchange,topicExchange,deadLetterExchange).distinct().count()==3
            && Stream.of(despacho,notificaciones,auditoria).flatMap(r -> Stream.of(r.queue(),r.deadLetterQueue())).distinct().count()==6
            && Stream.of(despacho,notificaciones,auditoria).map(Ruta::deadLetterKey).distinct().count()==3;
    }
}
