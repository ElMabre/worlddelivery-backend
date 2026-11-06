package cl.duoc.worlddelivery_usuarios.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarRolRequest(@NotBlank @Pattern(regexp = "Admin|Usuario") String rol) {}
