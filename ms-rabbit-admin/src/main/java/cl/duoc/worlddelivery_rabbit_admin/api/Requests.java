package cl.duoc.worlddelivery_rabbit_admin.api;
import jakarta.validation.constraints.*;
public final class Requests {
    private Requests() {}
    public static final String NAME="(?!amq[.])[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
    public record QueueRequest(@NotBlank @Pattern(regexp=NAME) String name) {}
    public enum ExchangeType { DIRECT, TOPIC, FANOUT }
    public record ExchangeRequest(@NotBlank @Pattern(regexp=NAME) String name,@NotNull ExchangeType type) {}
    public record BindingRequest(@NotBlank @Pattern(regexp=NAME) String queue,
        @NotBlank @Pattern(regexp=NAME) String exchange,
        @NotNull @Size(max=128) @Pattern(regexp="[A-Za-z0-9._*#-]*") String routingKey) {}
    public record QueueStatus(String name,int messagesReady,int consumers) {}
}
