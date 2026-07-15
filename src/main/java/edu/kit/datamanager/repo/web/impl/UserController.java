package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.domain.LocalRole;
import edu.kit.datamanager.repo.domain.LocalUser;
import edu.kit.datamanager.repo.repository.LocalUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRATOR')")
public class UserController {
    private final LocalUserRepository users;
    private final PasswordEncoder passwords;
    public UserController(LocalUserRepository users, PasswordEncoder passwords) { this.users = users; this.passwords = passwords; }

    @GetMapping public List<UserView> list() { return users.findAll().stream().map(UserView::from).toList(); }
    @PostMapping public ResponseEntity<UserView> create(@Valid @RequestBody UserRequest request) {
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        if (request.password() == null || request.password().length() < 8) return ResponseEntity.badRequest().build();
        LocalUser user = new LocalUser(username, passwords.encode(request.password()), request.role());
        user.setEnabled(request.enabled() == null || request.enabled());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserView.from(users.save(user)));
    }
    @PutMapping("/{id}") public ResponseEntity<UserView> update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return users.findById(id).map(user -> {
            String username = request.username().trim();
            boolean isSelf = user.getUsername().equalsIgnoreCase(SecurityContextHolder.getContext().getAuthentication().getName());
            if (isSelf && request.role() != user.getRole()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).<UserView>build();
            users.findByUsernameIgnoreCase(username).filter(other -> !other.getId().equals(id)).ifPresent(other -> { throw new DuplicateUserException(); });
            user.setUsername(username); if (!isSelf) user.setRole(request.role());
            if (request.enabled() != null) user.setEnabled(request.enabled());
            if (request.password() != null && !request.password().isBlank()) return ResponseEntity.status(HttpStatus.FORBIDDEN).<UserView>build();
            return ResponseEntity.ok(UserView.from(users.save(user)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        LocalUser user = users.findById(id).orElse(null); if (user == null) return ResponseEntity.notFound().build();
        if (user.getUsername().equalsIgnoreCase(SecurityContextHolder.getContext().getAuthentication().getName())) return ResponseEntity.badRequest().build();
        users.delete(user); return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(DuplicateUserException.class) ResponseEntity<Void> duplicate() { return ResponseEntity.status(HttpStatus.CONFLICT).build(); }
    static class DuplicateUserException extends RuntimeException {}
    public record UserRequest(@NotBlank String username, String password, @NotNull LocalRole role, Boolean enabled) {}
    public record UserView(Long id, String username, String email, LocalRole role, boolean enabled, boolean verified, Instant createdAt) { static UserView from(LocalUser user) { return new UserView(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.isEnabled(), user.isVerified(), user.getCreatedAt()); } }
}
