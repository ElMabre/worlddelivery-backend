package cl.duoc.worlddelivery_operaciones.messaging;
import cl.duoc.worlddelivery_operaciones.config.ProcesamientoProperties;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validator;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import java.io.IOException;
import java.util.function.Consumer;
@Component
public class ProcesadorMensaje {
    private static final Logger log=LoggerFactory.getLogger(ProcesadorMensaje.class);
    private final ObjectMapper json;
    private final Validator validator;
    private final ProcesamientoProperties config;
    public ProcesadorMensaje(ObjectMapper json,Validator validator,ProcesamientoProperties config) {
        this.json=json; this.validator=validator; this.config=config;
    }
    public void procesar(Dominio dominio,Message message,Channel channel,Consumer<EventoPedido> accion) throws IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        EventoPedido evento;
        try {
            evento=json.readValue(message.getBody(),EventoPedido.class);
            if (evento==null || !validator.validate(evento).isEmpty()) {
                rechazar(channel,tag,dominio,"sin-validar","VALIDACION"); return;
            }
        } catch (JacksonException ex) {
            rechazar(channel,tag,dominio,"sin-validar","JSON_INVALIDO"); return;
        }
        String eventId=evento.eventId().toString();
        if (!dominio.acepta(evento.tipo())) {
            rechazar(channel,tag,dominio,eventId,"TIPO_NO_ADMITIDO"); return;
        }
        for (int intento=1;intento<=config.maxRetries()+1;intento++) {
            log.info("PROCESANDO dominio={} eventId={} intento={}",dominio,eventId,intento);
            try {
                if (config.demoFailureEnabled() && (evento.pedido().producto().contains("[ERROR-DLQ]")
                    || evento.pedido().direccion().contains("[ERROR-DLQ]"))) {
                    throw new FalloRecuperable("Fallo controlado para demostración");
                }
                accion.accept(evento);
            } catch (FalloRecuperable ex) {
                if (intento<=config.maxRetries()) {
                    log.warn("REINTENTO dominio={} eventId={} siguienteIntento={} causa={}",dominio,eventId,intento+1,ex.getClass().getSimpleName());
                    continue;
                }
                rechazar(channel,tag,dominio,eventId,"REINTENTOS_AGOTADOS"); return;
            } catch (FalloPermanente ex) {
                rechazar(channel,tag,dominio,eventId,"NEGOCIO"); return;
            } catch (RuntimeException ex) {
                log.error("FALLO_NO_RECUPERABLE dominio={} eventId={} clase={}",dominio,eventId,ex.getClass().getSimpleName());
                rechazar(channel,tag,dominio,eventId,"NO_RECUPERABLE"); return;
            }
            // Un fallo del canal durante el ACK no debe provocar un segundo NACK.
            channel.basicAck(tag,false);
            log.info("ACK_ENVIADO dominio={} eventId={}",dominio,eventId);
            return;
        }
    }
    private void rechazar(Channel channel,long tag,Dominio dominio,String eventId,String causa) throws IOException {
        channel.basicNack(tag,false,false);
        log.warn("NACK_DLQ_SOLICITADO dominio={} eventId={} causa={}",dominio,eventId,causa);
    }
}
