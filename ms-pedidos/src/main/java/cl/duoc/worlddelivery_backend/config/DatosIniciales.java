package cl.duoc.worlddelivery_backend.config;
import cl.duoc.worlddelivery_backend.domain.Pedido;
import cl.duoc.worlddelivery_backend.dto.CrearPedidoRequest;
import cl.duoc.worlddelivery_backend.repository.PedidoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
@Component
@ConditionalOnProperty(name="app.seed.enabled",havingValue="true")
public class DatosIniciales implements CommandLineRunner {
    private final PedidoRepository pedidos;
    private final ObjectMapper json;
    public DatosIniciales(PedidoRepository pedidos,ObjectMapper json) { this.pedidos=pedidos; this.json=json; }
    @Override @Transactional
    public void run(String... args) throws Exception {
        if (pedidos.count()!=0) return;
        try (var input=new ClassPathResource("pedidos-iniciales.json").getInputStream()) {
            for (var dto:json.readValue(input,CrearPedidoRequest[].class)) pedidos.save(new Pedido(dto));
        }
    }
}
