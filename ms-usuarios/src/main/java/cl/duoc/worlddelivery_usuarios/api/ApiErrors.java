package cl.duoc.worlddelivery_usuarios.api;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    ResponseEntity<ProblemDetail> invalid() {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Username o rol inválido."));
    }
    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ProblemDetail> missing() {
        return ResponseEntity.status(404).body(ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> business(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
            .body(ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason()));
    }
    @ExceptionHandler(SdkException.class)
    ResponseEntity<ProblemDetail> aws() {
        return ResponseEntity.status(503).body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "Cognito no confirmó la operación. Puede haberse aplicado parcialmente; actualiza la lista antes de repetir."));
    }
}
