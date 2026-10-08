package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.domain.ScientificMetadataProfileDefinition;
import edu.kit.datamanager.repo.service.ScientificMetadataProfileService;
import edu.kit.datamanager.repo.service.ScientificMetadataProfileService.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/scientific/metadata-profiles")
public class ScientificMetadataProfileController {
    private final ScientificMetadataProfileService profiles;
    public ScientificMetadataProfileController(ScientificMetadataProfileService profiles){this.profiles=profiles;}
    @ModelAttribute public void noStore(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public List<ApprovedView> available(){return profiles.available();}
    @GetMapping("/fields") public Map<String,String> fields(){return ScientificMetadataProfileService.FIELDS;}
    @GetMapping("/administration") @PreAuthorize("hasAuthority('ROLE_ADMINISTRATOR')") public List<AdminView> administration(){return profiles.administration();}
    @PutMapping("/administration/{id}") @PreAuthorize("hasAuthority('ROLE_ADMINISTRATOR')") public AdminView propose(@PathVariable String id,@RequestBody Proposal input){return profiles.propose(id,input.definition(),input.requiredFields(),input.note(),input.revision(),actor());}
    @PostMapping("/administration/{id}/approve") @PreAuthorize("hasAuthority('ROLE_ADMINISTRATOR')") public AdminView approve(@PathVariable String id,@RequestBody Approval input){return profiles.approve(id,input.revision(),actor());}
    @PostMapping("/administration/{id}/active") @PreAuthorize("hasAuthority('ROLE_ADMINISTRATOR')") public AdminView active(@PathVariable String id,@RequestBody Active input){if(input.active()==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Indique si el perfil debe estar activo.");return profiles.setActive(id,input.revision(),input.active(),actor());}
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class}) public org.springframework.http.ResponseEntity<?> conflict(){return org.springframework.http.ResponseEntity.status(409).body(Map.of("message","El perfil cambió; vuelva a consultarlo antes de guardar."));}
    private String actor(){return SecurityContextHolder.getContext().getAuthentication().getName();}
    @io.swagger.v3.oas.annotations.media.Schema(name="MetadataProfileProposal") public record Proposal(ScientificMetadataProfileDefinition definition,Set<String> requiredFields,String note,Long revision){}
    @io.swagger.v3.oas.annotations.media.Schema(name="MetadataProfileApproval") public record Approval(Long revision){}
    @io.swagger.v3.oas.annotations.media.Schema(name="MetadataProfileActivation") public record Active(Long revision,Boolean active){}
}
