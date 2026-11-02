package cl.duoc.worlddelivery_backend.config;
import cl.duoc.worlddelivery_backend.messaging.PublicadorPendientes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.outbox.enabled",havingValue="true",matchIfMissing=true)
public class PublicacionProgramada {
    private final PublicadorPendientes publicador;
    public PublicacionProgramada(PublicadorPendientes publicador) { this.publicador=publicador; }
    @Scheduled(fixedDelayString="${app.outbox.interval-ms:2000}")
    public void enviar() { publicador.publicarLote(); }
}
