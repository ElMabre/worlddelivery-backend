package cl.duoc.worlddelivery_backend.repository;
import cl.duoc.worlddelivery_backend.domain.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PedidoRepository extends JpaRepository<Pedido,Long> {
    java.util.List<Pedido> findByActivoOrderByIdAsc(boolean activo);
    java.util.Optional<Pedido> findByIdAndActivoTrue(Long id);
}
