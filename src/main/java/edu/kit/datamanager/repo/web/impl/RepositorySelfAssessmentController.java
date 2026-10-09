package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.service.RepositorySelfAssessmentService;
import edu.kit.datamanager.repo.domain.RepositorySelfAssessment;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/scientific/operations/self-assessment")
@PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
public class RepositorySelfAssessmentController {
 private final RepositorySelfAssessmentService service;
 public RepositorySelfAssessmentController(RepositorySelfAssessmentService service){this.service=service;}
 @GetMapping public ResponseEntity<RepositorySelfAssessmentService.Report> report(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.report());}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('ROLE_ADMINISTRATOR')")
 public RepositorySelfAssessmentService.Item update(@PathVariable String id,@RequestBody Input input){return service.save(id,input.revision(),input.state(),input.responsible(),input.statement(),input.evidence(),SecurityContextHolder.getContext().getAuthentication().getName());}
 @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class,org.springframework.dao.DataIntegrityViolationException.class})
 public ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("message","La evaluación cambió; recargue antes de guardar."));}
 public record Input(Long revision,RepositorySelfAssessment.State state,String responsible,String statement,List<String> evidence){}
}
