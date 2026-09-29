package cl.duoc.worlddelivery_backend.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final List<Map<String, Object>> pedidos = new ArrayList<>(List.of(
            Map.of("id", 1, "producto", "Notebook HP Envy", "estado", "EN PREPARACION", "cliente", "Camila Rojas", "direccion", "Av. Providencia 1234, Providencia", "fechaEntrega", "2026-10-02"),
            Map.of("id", 2, "producto", "Smartphone Samsung S24", "estado", "EN REPARTO", "cliente", "Felipe Soto", "direccion", "San Diego 456, Santiago Centro", "fechaEntrega", "2026-09-30"),
            Map.of("id", 3, "producto", "Silla Gamer", "estado", "EN TRANSITO", "cliente", "Diego Tapia", "direccion", "Vicuña Mackenna 1500, Ñuñoa", "fechaEntrega", "2026-10-01"),
            Map.of("id", 4, "producto", "Monitor Xiaomi G27\"", "estado", "ENTREGADO", "cliente", "Valentina Castro", "direccion", "Gran Avenida 4500, San Miguel", "fechaEntrega", "2026-09-28"),
            Map.of("id", 5, "producto", "Zapatillas de Running", "estado", "PENDIENTE", "cliente", "Andrés Morales", "direccion", "Av. Las Condes 7890, Las Condes", "fechaEntrega", "2026-10-05"),
            Map.of("id", 6, "producto", "Teclado Mecánico Keychron", "estado", "EN TRANSITO", "cliente", "Sofía Vergara", "direccion", "Av. Apoquindo 3200, Las Condes", "fechaEntrega", "2026-10-03"),
            Map.of("id", 7, "producto", "Cafetera Nespresso", "estado", "EN PREPARACION", "cliente", "Javier Silva", "direccion", "Paseo Ahumada 120, Santiago Centro", "fechaEntrega", "2026-10-04"),
            Map.of("id", 8, "producto", "Libro: 1984", "estado", "ENTREGADO", "cliente", "Natalia Reyes", "direccion", "Arturo Prat 890, Santiago Centro", "fechaEntrega", "2026-09-25"),
            Map.of("id", 9, "producto", "Pasta Térmica Arctic MX-4", "estado", "EN PREPARACION", "cliente", "Carlos Pinto", "direccion", "Franklin 560, Santiago Centro", "fechaEntrega", "2026-10-02"),
            Map.of("id", 10, "producto", "Perfume Bleu de Chanel", "estado", "EN REPARTO", "cliente", "María Paz Valdés", "direccion", "Alonso de Córdova 2345, Vitacura", "fechaEntrega", "2026-09-29"),
            Map.of("id", 11, "producto", "Kit Destornilladores de Precisión", "estado", "ENTREGADO", "cliente", "Rodrigo Vega", "direccion", "Av. Matta 1010, Santiago Centro", "fechaEntrega", "2026-09-27"),
            Map.of("id", 12, "producto", "Multímetro Digital", "estado", "EN TRANSITO", "cliente", "Ignacio Gómez", "direccion", "Santa Rosa 2050, San Joaquín", "fechaEntrega", "2026-10-01"),
            Map.of("id", 13, "producto", "SSD NVMe 1TB WD Black", "estado", "EN REPARTO", "cliente", "Sebastián Medina", "direccion", "Bandera 450, Santiago Centro", "fechaEntrega", "2026-09-30"),
            Map.of("id", 14, "producto", "Alcohol Isopropílico 1L", "estado", "PENDIENTE", "cliente", "Lorena Castillo", "direccion", "Av. Pajaritos 3100, Maipú", "fechaEntrega", "2026-10-06"),
            Map.of("id", 15, "producto", "Lupa de Cabeza con Luz LED", "estado", "ENTREGADO", "cliente", "Hugo Contreras", "direccion", "Av. Grecia 4300, Macul", "fechaEntrega", "2026-09-26"),
            Map.of("id", 16, "producto", "Audífonos Inalámbricos Sony", "estado", "EN TRANSITO", "cliente", "Margarita Orellana", "direccion", "Irarrazaval 2800, Ñuñoa", "fechaEntrega", "2026-10-03"),
            Map.of("id", 17, "producto", "Consola PlayStation 5", "estado", "EN PREPARACION", "cliente", "Tomás Herrera", "direccion", "Av. La Florida 8900, La Florida", "fechaEntrega", "2026-10-04"),
            Map.of("id", 18, "producto", "Set de Parrilla para Asado", "estado", "ENTREGADO", "cliente", "Claudio Mella", "direccion", "Av. Ossa 1500, La Reina", "fechaEntrega", "2026-09-25"),
            Map.of("id", 19, "producto", "Smart TV LG 55\"", "estado", "EN REPARTO", "cliente", "Patricia Cárdenas", "direccion", "Av. Vitacura 6700, Vitacura", "fechaEntrega", "2026-09-29"),
            Map.of("id", 20, "producto", "Alimento para Perro 15kg", "estado", "EN TRANSITO", "cliente", "Roberto Fuentes", "direccion", "Av. Independencia 1200, Independencia", "fechaEntrega", "2026-10-02"),
            Map.of("id", 21, "producto", "Juego de Sábanas 2 Plazas", "estado", "ENTREGADO", "cliente", "Daniela Araya", "direccion", "Av. Recoleta 2300, Recoleta", "fechaEntrega", "2026-09-28"),
            Map.of("id", 22, "producto", "Mochila para Notebook", "estado", "PENDIENTE", "cliente", "Luis Navarrete", "direccion", "Américo Vespucio 1500, Cerrillos", "fechaEntrega", "2026-10-07"),
            Map.of("id", 23, "producto", "Reloj Inteligente Garmin", "estado", "EN REPARTO", "cliente", "Carmen Gloria", "direccion", "Av. Los Leones 2100, Providencia", "fechaEntrega", "2026-09-30"),
            Map.of("id", 24, "producto", "Mouse Logitech G Pro X", "estado", "EN PREPARACION", "cliente", "Eduardo Carrasco", "direccion", "Av. Macul 3400, Macul", "fechaEntrega", "2026-10-05"),
            Map.of("id", 25, "producto", "Lentes de Realidad Virtual", "estado", "EN TRANSITO", "cliente", "Francisca Vargas", "direccion", "Av. Salvador 1100, Providencia", "fechaEntrega", "2026-10-04"),
            Map.of("id", 26, "producto", "Cámara Web 1080p", "estado", "ENTREGADO", "cliente", "Ricardo Guzmán", "direccion", "Av. Tobalaba 4500, Peñalolén", "fechaEntrega", "2026-09-24"),
            Map.of("id", 27, "producto", "Cargador USB-C 65W", "estado", "EN REPARTO", "cliente", "Fernanda López", "direccion", "Pedro de Valdivia 1800, Providencia", "fechaEntrega", "2026-09-29"),
            Map.of("id", 28, "producto", "Micrófono Condensador USB", "estado", "EN PREPARACION", "cliente", "Marcelo Paredes", "direccion", "Av. Kennedy 5600, Vitacura", "fechaEntrega", "2026-10-06")
    ));
    
    private int contadorId = 29;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/api-access')") 
    public List<Map<String, Object>> obtenerPedidos() {
        return pedidos;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/write')") 
    public ResponseEntity<Map<String, Object>> crearPedido(@RequestBody Map<String, Object> nuevoPedido) {
        Map<String, Object> pedidoMutable = new HashMap<>(nuevoPedido);
        pedidoMutable.put("id", contadorId++);
        pedidos.add(pedidoMutable);
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoMutable);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/update')") 
    public ResponseEntity<Map<String, Object>> actualizarEstado(@PathVariable int id, @RequestBody Map<String, String> payload) {
        for (int i = 0; i < pedidos.size(); i++) {
            Map<String, Object> pedido = pedidos.get(i);
            if ((Integer) pedido.get("id") == id) {
                Map<String, Object> pedidoMutable = new HashMap<>(pedido);
                pedidoMutable.put("estado", payload.get("estado"));
                pedidos.set(i, pedidoMutable);
                return ResponseEntity.ok(pedidoMutable);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_resource-server-worlddelivery/delete')") 
    public ResponseEntity<Void> eliminarPedido(@PathVariable int id) {
        boolean removido = pedidos.removeIf(p -> (Integer) p.get("id") == id);
        if (removido) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}