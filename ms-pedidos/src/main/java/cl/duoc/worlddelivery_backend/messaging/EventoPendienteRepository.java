package cl.duoc.worlddelivery_backend.messaging;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EventoPendienteRepository extends JpaRepository<EventoPendiente,Long> {
    List<EventoPendiente> findTop50ByOrderByIdAsc();
}
