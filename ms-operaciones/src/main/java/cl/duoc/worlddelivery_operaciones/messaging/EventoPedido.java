package cl.duoc.worlddelivery_operaciones.messaging;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Arrays;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
public record EventoPedido(@NotNull UUID eventId, @NotNull Tipo tipo, @NotNull Instant ocurridoEn,
                           @NotNull @Valid Pedido pedido) {
    public enum Tipo { CREADO, ESTADO_ACTUALIZADO, ELIMINADO, MODIFICADO }
    public enum Estado {
        PENDIENTE, EN_PREPARACION, EN_TRANSITO, EN_REPARTO, ENTREGADO;
        @JsonValue public String etiqueta() { return name().replace('_',' '); }
        @JsonCreator public static Estado desdeTexto(String valor) {
            return Arrays.stream(values()).filter(e -> e.etiqueta().equals(valor)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado inválido"));
        }
    }
    public record Pedido(@NotNull @Positive Long id, @NotBlank @Size(max=200) String producto,
        @NotBlank @Size(max=150) String cliente, @NotBlank @Size(max=300) String direccion,
        @NotNull LocalDate fechaEntrega, @NotNull Estado estado) {}
}
