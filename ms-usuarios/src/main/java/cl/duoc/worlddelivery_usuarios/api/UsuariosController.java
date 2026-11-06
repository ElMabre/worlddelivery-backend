package cl.duoc.worlddelivery_usuarios.api;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import cl.duoc.worlddelivery_usuarios.service.UsuariosService;

@RestController
@Validated
@RequestMapping("/api/usuarios")
public class UsuariosController {
    private final UsuariosService usuarios;
    public UsuariosController(UsuariosService usuarios) { this.usuarios = usuarios; }

    @GetMapping
    public List<UsuarioResponse> listar() { return usuarios.listar(); }

    @PatchMapping("/{username}/deshabilitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deshabilitar(@PathVariable @NotBlank @Size(max = 128) String username,
                              @AuthenticationPrincipal Jwt jwt) {
        usuarios.deshabilitar(username, jwt.getClaimAsString("username"));
    }

    @PatchMapping("/{username}/rol")
    public UsuarioResponse cambiarRol(@PathVariable @NotBlank @Size(max = 128) String username,
                                      @Valid @RequestBody CambiarRolRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return usuarios.cambiarRol(username, request.rol(), jwt.getClaimAsString("username"));
    }
}
