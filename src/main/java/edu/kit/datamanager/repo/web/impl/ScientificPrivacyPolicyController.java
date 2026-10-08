package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.service.ScientificPrivacyService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/scientific/privacy-policy")
public class ScientificPrivacyPolicyController {
    private final ScientificPrivacyService privacy;
    public ScientificPrivacyPolicyController(ScientificPrivacyService privacy) {this.privacy=privacy;}
    @GetMapping public java.util.Map<String,Boolean> policy() {return java.util.Map.of("required",privacy.isRequired());}
}
