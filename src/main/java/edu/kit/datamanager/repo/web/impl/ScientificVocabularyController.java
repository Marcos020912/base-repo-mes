package edu.kit.datamanager.repo.web.impl;

import java.util.List;
import java.util.Map;
import edu.kit.datamanager.repo.service.ScientificVocabularyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local deposition suggestions; institutional authority lists can replace these by configuration. */
@RestController
@RequestMapping("/api/v1/scientific/vocabularies")
public class ScientificVocabularyController {
    private final ScientificVocabularyService vocabularies;

    public ScientificVocabularyController(ScientificVocabularyService vocabularies) { this.vocabularies = vocabularies; }

    @GetMapping
    public Map<String, List<String>> list() {
        return Map.of("licenses", vocabularies.licenses(), "disciplines", vocabularies.disciplines());
    }
}
