package edu.kit.datamanager.repo.test.unit;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import edu.kit.datamanager.repo.service.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ScientificMetadataProfileServiceTest {
    @Test public void freezesApprovedRulesAndPreservesAuthorValues(){
        var profiles=mock(ScientificMetadataProfileRepository.class);var records=mock(ScientificRecordRepository.class);
        var state=new AtomicReference<ScientificMetadataProfile>();when(profiles.findById("tabular")).thenAnswer(call->Optional.ofNullable(state.get()));
        when(profiles.saveAndFlush(any())).thenAnswer(call->{ScientificMetadataProfile value=call.getArgument(0);value.setRevision(value.getRevision()+1);state.set(value);return value;});
        when(profiles.findByActiveTrueOrderByIdAsc()).thenAnswer(call->state.get()!=null&&state.get().isActive()?List.of(state.get()):List.of());
        var service=new ScientificMetadataProfileService(profiles,records,new ScientificVocabularyService("CC-BY-4.0","Física",true));
        var proposed=service.propose("tabular",new ScientificMetadataProfileDefinition("Tabular","Definición\nrevisada","CC-BY-4.0","es","Física"),Set.of("summary"),"Motivo",null,"admin");
        assertTrue(service.available().isEmpty());var approved=service.approve("tabular",proposed.revision(),"admin");
        var record=new ScientificRecord("data");record.setRevision(3);record.setLicenseId("Licencia anterior declarada");when(records.findById("data")).thenReturn(Optional.of(record));when(records.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
        var saved=service.apply("data","tabular",approved.approvedRevision(),3L);
        assertEquals("Licencia anterior declarada",saved.getLicenseId());assertEquals("es",saved.getLanguage());assertEquals(Set.of("summary"),saved.getMetadataProfileRequiredFields());assertFalse(ScientificMetadataProfileService.complete(saved,"summary"));
        var next=service.propose("tabular",new ScientificMetadataProfileDefinition("Nuevo",null,null,"en",null),Set.of("keywords"),"Otro motivo",approved.revision(),"admin");service.approve("tabular",next.revision(),"admin");
        assertEquals("Tabular",saved.getMetadataProfileName());assertEquals(Set.of("summary"),saved.getMetadataProfileRequiredFields());assertEquals("es",saved.getLanguage());
        var latest=state.get();service.setActive("tabular",latest.getRevision(),false,"admin");assertTrue(service.available().isEmpty());assertEquals(Set.of("summary"),saved.getMetadataProfileRequiredFields());
        record.setStatus(PublicationStatus.PUBLISHED);expectStatus(409,()->service.apply("data",null,null,3L));
    }
    @Test public void rejectsUnknownRulesIdentifiersDefaultsAndStaleSnapshots(){
        var profiles=mock(ScientificMetadataProfileRepository.class);var records=mock(ScientificRecordRepository.class);when(profiles.findById(anyString())).thenReturn(Optional.empty());
        var service=new ScientificMetadataProfileService(profiles,records,new ScientificVocabularyService("MIT","Física",true));
        var definition=new ScientificMetadataProfileDefinition("Perfil",null,"MIT","es",null);
        expectStatus(400,()->service.propose("../bad",definition,Set.of(),"Motivo",null,"admin"));
        expectStatus(400,()->service.propose("valid",definition,Set.of("privacyClassification"),"Motivo",null,"admin"));
        expectStatus(409,()->service.propose("valid",definition,Set.of(),"Motivo",2L,"admin"));
        var profile=new ScientificMetadataProfile("valid");profile.setProposed(new ScientificMetadataProfileDefinition("Perfil",null,"UNKNOWN","es",null));when(profiles.findById("valid")).thenReturn(Optional.of(profile));expectStatus(400,()->service.approve("valid",0L,"admin"));
        profile.setApproved(definition);profile.setApprovedRevision(3L);profile.setActive(true);var record=new ScientificRecord("data");record.setRevision(4);when(records.findById("data")).thenReturn(Optional.of(record));
        expectStatus(409,()->service.apply("data","valid",2L,4L));expectStatus(409,()->service.apply("data","valid",3L,3L));
        var input=new ScientificMetadataProfileDefinition("Perfil",null,"MIT","bad code",null);expectStatus(400,()->service.propose("new",input,Set.of(),"Motivo",null,"admin"));
        verify(records,never()).saveAndFlush(any());
    }
    @Test public void removingProfileDoesNotEraseUserMetadata(){
        var profiles=mock(ScientificMetadataProfileRepository.class);var records=mock(ScientificRecordRepository.class);var record=new ScientificRecord("data");record.setMetadataProfileId("old");record.setMetadataProfileRequiredFields(new LinkedHashSet<>(Set.of("summary")));record.setLicenseId("MIT");when(records.findById("data")).thenReturn(Optional.of(record));when(records.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
        var service=new ScientificMetadataProfileService(profiles,records,new ScientificVocabularyService("MIT","Física",true));service.apply("data",null,null,0L);assertNull(record.getMetadataProfileId());assertTrue(record.getMetadataProfileRequiredFields().isEmpty());assertEquals("MIT",record.getLicenseId());
    }
    private static void expectStatus(int status,Runnable operation){try{operation.run();fail("No rechazó operación");}catch(ResponseStatusException expected){assertEquals(status,expected.getStatusCode().value());}}
}
