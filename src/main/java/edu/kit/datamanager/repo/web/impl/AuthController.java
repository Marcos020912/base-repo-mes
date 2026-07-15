package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.domain.LocalUser;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import edu.kit.datamanager.repo.security.LocalJwtService;
import edu.kit.datamanager.repo.service.VerificationMailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final LocalUserRepository users;
    private final PasswordEncoder passwords;
    private final LocalJwtService tokens;
    private final VerificationMailService verification;
    public AuthController(LocalUserRepository users, PasswordEncoder passwords, LocalJwtService tokens, VerificationMailService verification) { this.users = users; this.passwords = passwords; this.tokens = tokens; this.verification=verification; }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LocalUser user = users.findByUsernameIgnoreCase(request.username().trim()).orElse(null);
        if (user != null && !user.isEnabled()) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ACCOUNT_RESTRICTED", "message", "Su cuenta fue restringida. Póngase en contacto con soporte.", "supportEmail", "soporte@mes.gob.cu"));
        if (user == null || !passwords.matches(request.password(), user.getPasswordHash())) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Usuario o contraseña inválidos"));
        if (!user.isVerified()) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "EMAIL_NOT_VERIFIED", "message", "Debe verificar su correo para acceder.", "email", user.getEmail()));
        return ResponseEntity.ok(new LoginResponse(tokens.create(user), UserController.UserView.from(user)));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username) || users.existsByEmailIgnoreCase(request.email().trim())) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "El usuario o correo ya está en uso."));
        if (request.password().length() < 8) return ResponseEntity.badRequest().body(Map.of("message", "La contraseña debe tener al menos 8 caracteres."));
        LocalUser user = new LocalUser(username, passwords.encode(request.password()), edu.kit.datamanager.repo.domain.LocalRole.USER);
        user.setEmail(request.email().trim().toLowerCase()); users.save(user); verification.createAndSend(user); users.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Cuenta creada. Revise su correo para verificarla.", "email", user.getEmail()));
    }
    @PostMapping("/verify") public ResponseEntity<?> verify(@Valid @RequestBody VerificationRequest request) { LocalUser user=users.findByEmailIgnoreCase(request.email().trim()).orElse(null); if(user==null || user.getVerificationCode()==null || !user.getVerificationCode().equals(request.code()) || user.getVerificationExpiresAt().isBefore(java.time.Instant.now())) return ResponseEntity.badRequest().body(Map.of("message","El código no es válido o venció.")); user.setVerified(true);user.setVerificationCode(null);user.setVerificationExpiresAt(null);users.save(user);return ResponseEntity.ok(Map.of("message","Correo verificado. Ya puede iniciar sesión.")); }
    @PostMapping("/resend-verification") public ResponseEntity<?> resend(@Valid @RequestBody EmailRequest request) { LocalUser user=users.findByEmailIgnoreCase(request.email().trim()).orElse(null); if(user==null) return ResponseEntity.ok(Map.of("message","Si el correo existe, recibirá un código.")); if(!user.isVerified()){verification.createAndSend(user);users.save(user);}return ResponseEntity.ok(Map.of("message","Si el correo existe, recibirá un código.")); }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegistrationRequest(@NotBlank String username, @Email @NotBlank String email, @NotBlank String password) {}
    public record VerificationRequest(@Email @NotBlank String email, @NotBlank String code) {}
    public record EmailRequest(@Email @NotBlank String email) {}
    public record LoginResponse(String token, UserController.UserView user) {}
}
