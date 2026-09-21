package cl.duoc.worlddelivery_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/api-access')")
    public String obtenerPedidos() {
        return "{\"mensaje\": \"Listado de pedidos WorldDelivery\"}";
    }
}