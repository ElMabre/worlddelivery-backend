package cl.duoc.worlddelivery_backend.domain;
import cl.duoc.worlddelivery_backend.dto.CrearPedidoRequest;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity
@Table(name="pedidos")
public class Pedido {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @Column(nullable=false,length=200) private String producto;
    @Column(nullable=false,length=150) private String cliente;
    @Column(nullable=false,length=300) private String direccion;
    @Column(nullable=false) private LocalDate fechaEntrega;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EstadoPedido estado;
    protected Pedido() {}
    public Pedido(CrearPedidoRequest r) {
        producto=r.producto().strip(); cliente=r.cliente().strip(); direccion=r.direccion().strip();
        fechaEntrega=r.fechaEntrega(); estado=r.estado();
    }
    public void cambiarEstado(EstadoPedido nuevo) { estado=nuevo; }
    public Long getId() { return id; }
    public String getProducto() { return producto; }
    public String getCliente() { return cliente; }
    public String getDireccion() { return direccion; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public EstadoPedido getEstado() { return estado; }
}
