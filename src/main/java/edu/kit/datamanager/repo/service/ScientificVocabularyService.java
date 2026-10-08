package edu.kit.datamanager.repo.service;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Configurable deposit vocabularies; strict validation is opt-in after institutional approval. */
@Service
public class ScientificVocabularyService {
    private final List<String> licenses;
    private final List<String> disciplines;
    private final boolean strict;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private edu.kit.datamanager.repo.repository.ScientificVocabularyRegistryRepository registry;

    public ScientificVocabularyService(
            @Value("${repo.scientific.licenses:CC-BY-4.0,CC-BY-SA-4.0,CC0-1.0,MIT,Apache-2.0}") String licenses,
            @Value("${repo.scientific.disciplines:Matemáticas,Física,Química,Biología,Medicina,Ingeniería,Ciencias de la computación,Ciencias sociales,Humanidades,Educación}") String disciplines,
            @Value("${repo.scientific.strict-vocabulary:false}") boolean strict) {
        this.licenses = split(licenses);
        this.disciplines = split(disciplines);
        this.strict = strict;
    }

    public List<String> licenses() { return effective(edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry.Kind.LICENSE,licenses); }
    public List<String> disciplines() { return effective(edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry.Kind.DISCIPLINE,disciplines); }
    public boolean validLicense(String value) { return !strict || value == null || licenses().contains(value); }
    public boolean validDiscipline(String value) { return !strict || value == null || disciplines().contains(value); }
    public boolean strict() { return strict; }

    public boolean validLicense(String value,String previous){return java.util.Objects.equals(value,previous)||validLicense(value);}
    public boolean validDiscipline(String value,String previous){return java.util.Objects.equals(value,previous)||validDiscipline(value);}
    private List<String> effective(edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry.Kind kind,List<String> fallback){
        return registry==null?fallback:registry.findById(kind).filter(edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry::isApproved).map(entry->List.copyOf(entry.getApprovedValues())).orElse(fallback);
    }
    private static List<String> split(String values) {
        return Arrays.stream(values.split(",")).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }
}
