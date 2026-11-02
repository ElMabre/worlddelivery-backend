package cl.duoc.worlddelivery_operaciones.storage;
import cl.duoc.worlddelivery_operaciones.config.ProcesamientoProperties;
import cl.duoc.worlddelivery_operaciones.messaging.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.io.IOException;
import java.util.Arrays;
@Component
public class RegistroOperaciones {
    private final Path base;
    private final ObjectMapper json;
    public RegistroOperaciones(ProcesamientoProperties config,ObjectMapper json) {
        base=Path.of(config.storageDirectory()).toAbsolutePath().normalize(); this.json=json;
    }
    // Un archivo por eventId y dominio: el resultado es también el registro idempotente.
    public boolean guardar(Dominio dominio,EventoPedido evento) {
        Path folder=base.resolve(dominio.carpeta());
        Path target=folder.resolve(evento.eventId()+".json");
        Path temporary=null;
        try {
            byte[] content=json.writeValueAsBytes(evento);
            Files.createDirectories(folder);
            if (Files.exists(target)) return comparar(target,content);
            temporary=Files.createTempFile(folder,"evento-",".tmp");
            Files.write(temporary,content);
            // No aceptar una escritura parcial como operación completada tras reiniciar.
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (FileAlreadyExistsException ex) {
            try { return comparar(target,json.writeValueAsBytes(evento)); }
            catch (IOException io) { throw new FalloRecuperable("No se pudo consultar el registro",io); }
        } catch (IOException ex) {
            throw new FalloRecuperable("No se pudo guardar el resultado local",ex);
        } finally {
            if (temporary!=null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* No invalidar un resultado confirmado. */ }
            }
        }
    }
    private boolean comparar(Path target,byte[] content) throws IOException {
        if (!Arrays.equals(Files.readAllBytes(target),content)) throw new FalloPermanente("eventId repetido con contenido diferente");
        return false;
    }
}
