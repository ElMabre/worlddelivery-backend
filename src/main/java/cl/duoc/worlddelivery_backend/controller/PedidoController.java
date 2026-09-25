package cl.duoc.worlddelivery_backend.controller;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/api-access')")
    public List<Map<String, Object>> obtenerPedidos() {
        return List.of(
            Map.of("id", 1, "producto", "Notebook HP Envy", "estado", "EN PREPARACION"),
            Map.of("id", 2, "producto", "Smartphone Samsung S24", "estado", "EN REPARTO"),
            Map.of("id", 3, "producto", "Silla Gamer", "estado", "EN TRANSITO"),
            Map.of("id", 4, "producto", "Monitor Xiaomi G27\"", "estado", "ENTREGADO"),
            Map.of("id", 5, "producto", "Zapatillas de Running", "estado", "PENDIENTE"),
            Map.of("id", 6, "producto", "Teclado Mecánico Keychron", "estado", "EN TRANSITO"),
            Map.of("id", 7, "producto", "Cafetera Nespresso", "estado", "EN PREPARACION"),
            Map.of("id", 8, "producto", "Libro: 1984", "estado", "ENTREGADO"),
            Map.of("id", 9, "producto", "Pasta Térmica Arctic MX-4", "estado", "EN PREPARACION"),
            Map.of("id", 10, "producto", "Perfume Bleu de Chanel", "estado", "EN REPARTO"),
            Map.of("id", 11, "producto", "Kit Destornilladores de Precisión", "estado", "ENTREGADO"),
            Map.of("id", 12, "producto", "Multímetro Digital", "estado", "EN TRANSITO"),
            Map.of("id", 13, "producto", "SSD NVMe 1TB WD Black", "estado", "EN REPARTO"),
            Map.of("id", 14, "producto", "Alcohol Isopropílico 1L", "estado", "PENDIENTE"),
            Map.of("id", 15, "producto", "Lupa de Cabeza con Luz LED", "estado", "ENTREGADO"),
            Map.of("id", 16, "producto", "Audífonos Inalámbricos Sony", "estado", "EN TRANSITO"),
            Map.of("id", 17, "producto", "Consola PlayStation 5", "estado", "EN PREPARACION"),
            Map.of("id", 18, "producto", "Set de Parrilla para Asado", "estado", "ENTREGADO"),
            Map.of("id", 19, "producto", "Smart TV LG 55\"", "estado", "EN REPARTO"),
            Map.of("id", 20, "producto", "Alimento para Perro 15kg", "estado", "EN TRANSITO"),
            Map.of("id", 21, "producto", "Juego de Sábanas 2 Plazas", "estado", "ENTREGADO"),
            Map.of("id", 22, "producto", "Mochila para Notebook", "estado", "PENDIENTE"),
            Map.of("id", 23, "producto", "Reloj Inteligente Garmin", "estado", "EN REPARTO"),
            Map.of("id", 24, "producto", "Mouse Logitech G Pro X", "estado", "EN PREPARACION"),
            Map.of("id", 25, "producto", "Lentes de Realidad Virtual", "estado", "EN TRANSITO"),
            Map.of("id", 26, "producto", "Cámara Web 1080p", "estado", "ENTREGADO"),
            Map.of("id", 27, "producto", "Cargador USB-C 65W", "estado", "EN REPARTO"),
            Map.of("id", 28, "producto", "Micrófono Condensador USB", "estado", "EN PREPARACION")
        );
    }
}