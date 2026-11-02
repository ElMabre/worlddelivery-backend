package cl.duoc.worlddelivery_backend.dto;
import cl.duoc.worlddelivery_backend.domain.EstadoPedido;
import jakarta.validation.constraints.NotNull;
public record ActualizarEstadoRequest(@NotNull EstadoPedido estado) {}
