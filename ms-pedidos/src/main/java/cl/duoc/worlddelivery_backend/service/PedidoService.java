package cl.duoc.worlddelivery_backend.service;
import cl.duoc.worlddelivery_backend.domain.Pedido;
import cl.duoc.worlddelivery_backend.dto.*;
import cl.duoc.worlddelivery_backend.repository.PedidoRepository;
import cl.duoc.worlddelivery_backend.messaging.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
@Service
@Transactional
public class PedidoService {
    private final PedidoRepository pedidos;
    private final RegistroEventos eventos;
    public PedidoService(PedidoRepository pedidos, RegistroEventos eventos) { this.pedidos=pedidos; this.eventos=eventos; }
    @Transactional(readOnly=true)
    public List<PedidoResponse> listar() {
        return pedidos.findAll(Sort.by("id")).stream().map(PedidoResponse::desde).toList();
    }
    public PedidoResponse crear(CrearPedidoRequest request) {
        var respuesta=PedidoResponse.desde(pedidos.save(new Pedido(request)));
        eventos.registrar(PedidoEvento.Tipo.CREADO,respuesta);
        return respuesta;
    }
    public PedidoResponse actualizar(Long id, ActualizarEstadoRequest request) {
        var pedido=buscar(id);
        pedido.cambiarEstado(request.estado());
        var respuesta=PedidoResponse.desde(pedido);
        eventos.registrar(PedidoEvento.Tipo.ESTADO_ACTUALIZADO,respuesta);
        return respuesta;
    }
    public void eliminar(Long id) {
        var pedido=buscar(id);
        eventos.registrar(PedidoEvento.Tipo.ELIMINADO,PedidoResponse.desde(pedido));
        pedidos.delete(pedido);
    }
    private Pedido buscar(Long id) {
        return pedidos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido no encontrado"));
    }
}
