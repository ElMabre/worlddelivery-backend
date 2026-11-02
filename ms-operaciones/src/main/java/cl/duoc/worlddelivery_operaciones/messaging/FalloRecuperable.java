package cl.duoc.worlddelivery_operaciones.messaging;
public class FalloRecuperable extends RuntimeException {
    public FalloRecuperable(String message) { super(message); }
    public FalloRecuperable(String message,Throwable cause) { super(message,cause); }
}
