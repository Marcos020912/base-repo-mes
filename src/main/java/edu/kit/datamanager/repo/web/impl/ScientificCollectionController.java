package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.domain.ScientificCollection;
import edu.kit.datamanager.repo.service.ScientificCollectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/collections")
@PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
public class ScientificCollectionController {
    private final ScientificCollectionService service;
    public ScientificCollectionController(ScientificCollectionService service) {this.service=service;}
    public record CollectionRequest(@NotBlank @Size(max=200) String title,@Size(max=2000) String description,@NotNull ScientificCollection.Kind kind,boolean published,Long revision) {}
    @GetMapping public ScientificCollectionService.CollectionPage list(@RequestParam(defaultValue="") String kind,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.list(false,kind,page,size);}
    @GetMapping("/{id}") public ScientificCollectionService.DatasetPage detail(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.datasets(id,false,page,size);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ResponseEntity<ScientificCollectionService.CollectionView> create(@Valid @RequestBody CollectionRequest r) {return ResponseEntity.status(201).body(service.create(r.title(),r.description(),r.kind(),r.published()));}
    @PutMapping("/{id}") public ScientificCollectionService.CollectionView update(@PathVariable String id,@Valid @RequestBody CollectionRequest r) {
        if(r.revision()==null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Actualiza la colección antes de editar.");
        return service.update(id,r.revision(),r.title(),r.description(),r.kind(),r.published());
    }
    @PutMapping("/{id}/datasets/{resourceId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void add(@PathVariable String id,@PathVariable String resourceId) {service.add(id,resourceId);}
    @DeleteMapping("/{id}/datasets/{resourceId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable String id,@PathVariable String resourceId) {service.remove(id,resourceId);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id) {service.delete(id);}
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class}) public ResponseEntity<?> conflict() {return ResponseEntity.status(409).body(java.util.Map.of("message","La colección cambió. Actualiza la página y vuelve a intentarlo."));}
}
