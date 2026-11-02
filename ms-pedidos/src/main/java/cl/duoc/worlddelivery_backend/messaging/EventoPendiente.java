package cl.duoc.worlddelivery_backend.messaging;
import jakarta.persistence.*;
@Entity
@Table(name="eventos_pendientes")
public class EventoPendiente {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=36) private String eventId;
    @Column(nullable=false) private String exchangeName;
    @Column(nullable=false) private String routingKey;
    @Column(nullable=false,length=8192) private String payload;
    protected EventoPendiente() {}
    public EventoPendiente(String eventId, String exchange, String key, String payload) {
        this.eventId=eventId; this.exchangeName=exchange; this.routingKey=key; this.payload=payload;
    }
    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getExchangeName() { return exchangeName; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
}
