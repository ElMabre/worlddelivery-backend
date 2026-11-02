package cl.duoc.worlddelivery_backend.controller;
import cl.duoc.worlddelivery_backend.dto.*;
import cl.duoc.worlddelivery_backend.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private final PedidoService service;
    public PedidoController(PedidoService service) { this.service=service; }
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/api-access')")
    public List<PedidoResponse> obtenerPedidos() { return service.listar(); }
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/write')")
    public ResponseEntity<PedidoResponse> crearPedido(@Valid @RequestBody CrearPedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/update')")
    public PedidoResponse actualizarEstado(@PathVariable Long id,@Valid @RequestBody ActualizarEstadoRequest request) {
        return service.actualizar(id,request);
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/delete')")
    public ResponseEntity<Void> eliminarPedido(@PathVariable Long id) {
        service.eliminar(id); return ResponseEntity.noContent().build();
    }
}
