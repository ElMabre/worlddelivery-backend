package cl.duoc.worlddelivery_backend.dto;
import cl.duoc.worlddelivery_backend.domain.EstadoPedido;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record CrearPedidoRequest(
    @NotBlank @Size(max=200) String producto,
    @NotBlank @Size(max=150) String cliente,
    @NotBlank @Size(max=300) String direccion,
    @NotNull LocalDate fechaEntrega,
    @NotNull EstadoPedido estado
) {}
