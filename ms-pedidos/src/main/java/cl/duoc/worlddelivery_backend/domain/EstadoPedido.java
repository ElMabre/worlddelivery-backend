package cl.duoc.worlddelivery_backend.domain;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
public enum EstadoPedido {
    PENDIENTE, EN_PREPARACION, EN_TRANSITO, EN_REPARTO, ENTREGADO;
    @JsonValue public String etiqueta() { return name().replace('_', ' '); }
    public String claveEvento() { return name().toLowerCase(Locale.ROOT); }
    @JsonCreator public static EstadoPedido desdeTexto(String valor) {
        return Arrays.stream(values()).filter(e -> e.etiqueta().equals(valor)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Estado de pedido inválido"));
    }
}
