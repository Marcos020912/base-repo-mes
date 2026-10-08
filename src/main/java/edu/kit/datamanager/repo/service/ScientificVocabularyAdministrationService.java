package edu.kit.datamanager.repo.service;
import edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry;
import edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry.Kind;
import edu.kit.datamanager.repo.repository.ScientificVocabularyRegistryRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ScientificVocabularyAdministrationService {
    private final ScientificVocabularyRegistryRepository registry;
    private final ScientificVocabularyService vocabulary;
    public ScientificVocabularyAdministrationService(ScientificVocabularyRegistryRepository registry,ScientificVocabularyService vocabulary){this.registry=registry;this.vocabulary=vocabulary;}
    @Transactional(readOnly=true) public List<View> list(){return Arrays.stream(Kind.values()).map(kind->view(kind,registry.findById(kind).orElse(null))).toList();}
    @Transactional public View propose(Kind kind,List<String> values,String note,Long revision,String actor){
        var entry=registry.findById(kind).orElse(null);check(entry,revision);
        List<String> cleaned=normalize(kind,values);
        if(note==null||note.isBlank()||note.strip().length()>1000)throw bad("Explique la propuesta en un máximo de 1000 caracteres.");
        if(entry==null)entry=new ScientificVocabularyRegistry(kind);
        entry.setProposedValues(new ArrayList<>(cleaned));entry.setProposalNote(note.strip());entry.setProposedBy(actor);entry.setProposedAt(Instant.now());
        return view(kind,registry.saveAndFlush(entry));
    }
    @Transactional public View approve(Kind kind,Long revision,String actor){
        var entry=registry.findById(kind).orElse(null);if(entry==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No hay propuesta pendiente.");check(entry,revision);
        if(entry.getProposedValues().isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No hay propuesta pendiente.");
        entry.setApprovedValues(new ArrayList<>(entry.getProposedValues()));entry.setProposedValues(new ArrayList<>());entry.setApproved(true);entry.setApprovedBy(actor);entry.setApprovedAt(Instant.now());
        return view(kind,registry.saveAndFlush(entry));
    }
    static List<String> normalize(Kind kind,List<String> values){
        if(values==null||values.isEmpty()||values.size()>100)throw bad("Indique entre uno y cien términos.");
        var unique=new HashSet<String>();var result=new ArrayList<String>();
        for(String value:values){if(value==null)throw bad("No se admiten términos vacíos.");String clean=value.strip();
            if(clean.isEmpty()||clean.length()>(kind==Kind.LICENSE?100:255)||clean.codePoints().anyMatch(Character::isISOControl))throw bad("Hay términos vacíos, demasiado largos o con caracteres de control.");
            if(!unique.add(clean.toLowerCase(Locale.ROOT)))throw bad("No repita términos, incluso con distintas mayúsculas.");result.add(clean);}
        return List.copyOf(result);
    }
    private static void check(ScientificVocabularyRegistry entry,Long revision){if(entry==null?revision!=null:revision==null||revision!=entry.getRevision())throw new ResponseStatusException(HttpStatus.CONFLICT,"La lista cambió; vuelva a consultarla antes de guardar.");}
    private View view(Kind kind,ScientificVocabularyRegistry entry){List<String> effective=kind==Kind.LICENSE?vocabulary.licenses():vocabulary.disciplines();return new View(kind,entry==null?null:entry.getRevision(),effective,entry==null?List.of():List.copyOf(entry.getProposedValues()),entry!=null&&entry.isApproved()?"APPROVED_REGISTRY":"APPLICATION_PROPERTIES",entry==null?null:entry.getProposalNote(),entry==null?null:entry.getProposedBy(),entry==null?null:entry.getProposedAt(),entry==null?null:entry.getApprovedBy(),entry==null?null:entry.getApprovedAt(),vocabulary.strict());}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    @io.swagger.v3.oas.annotations.media.Schema(name="ScientificVocabularyAdministrationView")
    public record View(Kind kind,Long revision,List<String> effectiveValues,List<String> proposedValues,String source,String proposalNote,String proposedBy,Instant proposedAt,String approvedBy,Instant approvedAt,boolean strict){}
}
