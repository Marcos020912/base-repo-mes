package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry.Kind;
import edu.kit.datamanager.repo.service.ScientificVocabularyAdministrationService;
import edu.kit.datamanager.repo.service.ScientificVocabularyAdministrationService.View;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/scientific/vocabularies/administration") @PreAuthorize("hasAuthority('ROLE_ADMINISTRATOR')")
public class ScientificVocabularyAdministrationController {
    private final ScientificVocabularyAdministrationService service;
    public ScientificVocabularyAdministrationController(ScientificVocabularyAdministrationService service){this.service=service;}
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    public org.springframework.http.ResponseEntity<?> conflict(){return org.springframework.http.ResponseEntity.status(409).body(java.util.Map.of("message","El vocabulario cambió; vuelva a consultarlo antes de guardar."));}
    @ModelAttribute public void noStore(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public List<View> list(){return service.list();}
    @PutMapping("/{kind}") public View propose(@PathVariable Kind kind,@RequestBody Proposal input){return service.propose(kind,input.values(),input.note(),input.revision(),actor());}
    @PostMapping("/{kind}/approve") public View approve(@PathVariable Kind kind,@RequestBody Approval input){return service.approve(kind,input.revision(),actor());}
    private String actor(){return SecurityContextHolder.getContext().getAuthentication().getName();}
    @io.swagger.v3.oas.annotations.media.Schema(name="ScientificVocabularyProposal")
    public record Proposal(List<String> values,String note,Long revision){}
    @io.swagger.v3.oas.annotations.media.Schema(name="ScientificVocabularyApproval")
    public record Approval(Long revision){}
}
