package cl.duoc.worlddelivery_rabbit_admin.api;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;
import org.springframework.amqp.AmqpException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String message) {}
    @ExceptionHandler({MethodArgumentNotValidException.class,HandlerMethodValidationException.class,HttpMessageNotReadableException.class})
    ResponseEntity<ErrorBody> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("Solicitud invalida: revisar nombres, tipo de exchange y routingKey"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorBody> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ErrorBody(exception.getReason()));
    }
    @ExceptionHandler(AmqpException.class)
    ResponseEntity<ErrorBody> broker(AmqpException exception) {
        for(Throwable cause=exception;cause!=null;cause=cause.getCause()) {
            if(cause instanceof ShutdownSignalException shutdown && shutdown.getReason() instanceof AMQP.Channel.Close close) {
                if(close.getReplyCode()==404) return ResponseEntity.status(404).body(new ErrorBody("Recurso RabbitMQ no encontrado"));
                if(close.getReplyCode()==406) return ResponseEntity.status(409).body(new ErrorBody("El recurso existe con otra configuracion o no cumple las condiciones"));
            }
        }
        return ResponseEntity.status(503).body(new ErrorBody("RabbitMQ no disponible o acceso rechazado; verificar antes de repetir"));
    }
}
