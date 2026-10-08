package edu.kit.datamanager.repo.test.unit;
import edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry;
import edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry.Kind;
import edu.kit.datamanager.repo.repository.ScientificVocabularyRegistryRepository;
import edu.kit.datamanager.repo.service.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ScientificVocabularyAdministrationServiceTest {
    @Test public void proposalDoesNotReplaceEffectiveListBeforeExplicitApproval(){
        var repo=mock(ScientificVocabularyRegistryRepository.class);var state=new AtomicReference<ScientificVocabularyRegistry>();
        when(repo.findById(Kind.LICENSE)).thenAnswer(call->Optional.ofNullable(state.get()));
        when(repo.saveAndFlush(any())).thenAnswer(call->{ScientificVocabularyRegistry value=call.getArgument(0);value.setRevision(value.getRevision()+1);state.set(value);return value;});
        var vocabulary=new ScientificVocabularyService("CC-BY-4.0","Física",true);ReflectionTestUtils.setField(vocabulary,"registry",repo);
        var service=new ScientificVocabularyAdministrationService(repo,vocabulary);
        var draft=service.propose(Kind.LICENSE,List.of(" CC0-1.0 "),"Propuesta institucional",null,"admin-a");
        assertEquals(List.of("CC-BY-4.0"),draft.effectiveValues());assertEquals(List.of("CC0-1.0"),draft.proposedValues());assertEquals("APPLICATION_PROPERTIES",draft.source());
        var approved=service.approve(Kind.LICENSE,draft.revision(),"admin-b");assertEquals(List.of("CC0-1.0"),approved.effectiveValues());assertEquals("admin-b",approved.approvedBy());assertTrue(approved.proposedValues().isEmpty());
        service.propose(Kind.LICENSE,List.of("MIT"),"Siguiente propuesta",approved.revision(),"admin-a");assertEquals(List.of("CC0-1.0"),vocabulary.licenses());
        assertTrue(vocabulary.validLicense("CC-BY-4.0","CC-BY-4.0"));assertFalse(vocabulary.validLicense("CC-BY-4.0","MIT"));
    }
    @Test public void rejectsDuplicatesControlsEmptyListsAndStaleRevisions(){
        var repo=mock(ScientificVocabularyRegistryRepository.class);when(repo.findById(Kind.LICENSE)).thenReturn(Optional.empty());
        var service=new ScientificVocabularyAdministrationService(repo,new ScientificVocabularyService("MIT","Física",true));
        for(List<String> values:List.of(List.<String>of(),List.of("MIT","mit"),List.of("x\ny"),List.of(" "),List.of("x".repeat(101)))){
            try{service.propose(Kind.LICENSE,values,"Motivo",null,"admin");fail("Aceptó lista inválida");}catch(ResponseStatusException expected){assertEquals(400,expected.getStatusCode().value());}
        }
        try{service.propose(Kind.LICENSE,List.of("MIT"),"Motivo",4L,"admin");fail();}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}
        var entry=new ScientificVocabularyRegistry(Kind.LICENSE);entry.setRevision(3);when(repo.findById(Kind.LICENSE)).thenReturn(Optional.of(entry));
        try{service.approve(Kind.LICENSE,2L,"admin");fail();}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}
        try{service.approve(Kind.LICENSE,3L,"admin");fail();}catch(ResponseStatusException expected){assertEquals(409,expected.getStatusCode().value());}
        verify(repo,never()).saveAndFlush(any());
    }
    @Test public void emptyProposalAndInitialRevisionRemainExplicitInJson() throws Exception {
        var view=new ScientificVocabularyAdministrationService.View(Kind.LICENSE,null,List.of("MIT"),List.of(),"APPLICATION_PROPERTIES",null,null,null,null,null,true);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY);
        var json=mapper.readTree(mapper.writeValueAsString(view));
        assertTrue(json.has("proposedValues"));assertTrue(json.get("proposedValues").isArray());assertEquals(0,json.get("proposedValues").size());assertTrue(json.has("revision"));assertTrue(json.get("revision").isNull());
    }
}
