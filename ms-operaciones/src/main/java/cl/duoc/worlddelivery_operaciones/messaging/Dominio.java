package cl.duoc.worlddelivery_operaciones.messaging;
import java.util.Locale;
public enum Dominio {
    DESPACHO, NOTIFICACIONES, AUDITORIA;
    public String carpeta() { return name().toLowerCase(Locale.ROOT); }
    public boolean acepta(EventoPedido.Tipo tipo) {
        return this==AUDITORIA || (this==DESPACHO && tipo==EventoPedido.Tipo.CREADO)
            || (this==NOTIFICACIONES && tipo==EventoPedido.Tipo.ESTADO_ACTUALIZADO);
    }
}
