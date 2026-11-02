package cl.duoc.worlddelivery_backend.messaging;
import cl.duoc.worlddelivery_backend.dto.PedidoResponse;
import java.time.Instant;
public record PedidoEvento(String eventId, Tipo tipo, Instant ocurridoEn, PedidoResponse pedido) {
    public enum Tipo { CREADO, ESTADO_ACTUALIZADO, ELIMINADO }
}
