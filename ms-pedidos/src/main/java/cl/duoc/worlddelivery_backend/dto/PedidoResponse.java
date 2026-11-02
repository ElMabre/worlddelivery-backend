package cl.duoc.worlddelivery_backend.dto;
import cl.duoc.worlddelivery_backend.domain.*;
import java.time.LocalDate;
public record PedidoResponse(Long id, String producto, String cliente, String direccion,
                             LocalDate fechaEntrega, EstadoPedido estado) {
    public static PedidoResponse desde(Pedido p) {
        return new PedidoResponse(p.getId(), p.getProducto(), p.getCliente(), p.getDireccion(),
                                  p.getFechaEntrega(), p.getEstado());
    }
}
