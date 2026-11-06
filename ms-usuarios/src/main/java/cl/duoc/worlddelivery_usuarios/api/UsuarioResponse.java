package cl.duoc.worlddelivery_usuarios.api;

import java.util.List;
import java.util.Map;

public record UsuarioResponse(String username, boolean enabled, String status,
                              Map<String, String> atributos, List<String> grupos, String rol) {}
