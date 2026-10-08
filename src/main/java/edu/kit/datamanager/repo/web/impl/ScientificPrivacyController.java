package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.domain.ScientificPrivacyAssessment;
import edu.kit.datamanager.repo.service.ScientificPrivacyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/scientific/{id}/privacy")
public class ScientificPrivacyController {
    private final ScientificPrivacyService privacy;
    public ScientificPrivacyController(ScientificPrivacyService privacy) {this.privacy=privacy;}
    public record Declaration(ScientificPrivacyAssessment.Classification classification,String assessmentNote,Long revision) {}
    public record Review(boolean approved,String reviewNote,Long revision) {}
    @GetMapping public ScientificPrivacyService.Assessment read(@PathVariable String id) {return privacy.read(id);}
    @PutMapping public ScientificPrivacyService.Assessment save(@PathVariable String id,@RequestBody Declaration declaration) {
        return privacy.save(id,declaration.classification(),declaration.assessmentNote(),declaration.revision());
    }
    @PostMapping("/review") @PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
    public ScientificPrivacyService.Assessment review(@PathVariable String id,@RequestBody Review review) {
        return privacy.review(id,review.approved(),review.reviewNote(),review.revision());
    }
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public java.util.Map<String,String> conflict() {return java.util.Map.of("message","La evaluación cambió. Actualice y vuelva a intentarlo.");}
}
