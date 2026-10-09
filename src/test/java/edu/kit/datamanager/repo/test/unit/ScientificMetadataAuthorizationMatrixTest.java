package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import edu.kit.datamanager.repo.web.impl.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Controller-level matrix; HTTP filters and transactional races need separate integration evidence. */
@RunWith(Parameterized.class)
public class ScientificMetadataAuthorizationMatrixTest {
    @Parameterized.Parameters(name="{0}: role={1}, owner={2}")
    public static Collection<Object[]> cases() {
        List<Object[]> values=new ArrayList<>();
        for(PublicationStatus state:PublicationStatus.values()) {
            values.add(new Object[]{state,"ANONYMOUS",false});
            for(String role:List.of("USER","CURATOR","ADMINISTRATOR"))
                for(boolean owner:List.of(false,true)) values.add(new Object[]{state,role,owner});
        }
        return values;
    }
    private final PublicationStatus state; private final String role; private final boolean owner;
    public ScientificMetadataAuthorizationMatrixTest(PublicationStatus state,String role,boolean owner) {
        this.state=state;this.role=role;this.owner=owner;
    }
    private ScientificRecordRepository records;
    private ResourceOwnershipRepository ownership;
    private ScientificRecordEventRepository events;
    @Before public void setup() {
        SecurityContextHolder.clearContext();
        if(!role.equals("ANONYMOUS")) SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(owner?"author":"other","",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
        records=mock(ScientificRecordRepository.class);ownership=mock(ResourceOwnershipRepository.class);events=mock(ScientificRecordEventRepository.class);
        ScientificRecord record=new ScientificRecord("r1");record.setStatus(state);
        when(records.findById("r1")).thenReturn(Optional.of(record));
        when(ownership.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1","author")));
    }
    @After public void cleanup(){SecurityContextHolder.clearContext();}
    private void mutation(Runnable action,Object repository) {
        if(owner&&state==PublicationStatus.DRAFT) { action.run();verify(events).save(any()); }
        else {
            ResponseStatusException error=assertThrows(ResponseStatusException.class,action::run);
            assertEquals(owner?409:403,error.getStatusCode().value());
            verifyNoInteractions(repository,events);
        }
    }
    private void reading(Runnable action,Object repository) {
        boolean allowed=state==PublicationStatus.PUBLISHED||owner||role.equals("CURATOR")||role.equals("ADMINISTRATOR");
        if(allowed) action.run();
        else {assertEquals(404,assertThrows(ResponseStatusException.class,action::run).getStatusCode().value());verifyNoInteractions(repository);}
        verifyNoInteractions(events);
    }
    @Test public void relationsMutation() {
        var repository=mock(ScientificRelationRepository.class);when(repository.saveAll(any())).thenReturn(List.of());
        var controller=new ScientificRelationController(repository,records,ownership,events, org.mockito.Mockito.mock(edu.kit.datamanager.repo.service.ScientificResourceWriteLock.class));
        mutation(()->controller.replace("r1",List.of()),repository);
    }
    @Test public void fundingMutation() {
        var repository=mock(ScientificFundingRepository.class);when(repository.saveAll(any())).thenReturn(List.of());
        var controller=new ScientificFundingController(repository,records,ownership,events, org.mockito.Mockito.mock(edu.kit.datamanager.repo.service.ScientificResourceWriteLock.class));
        mutation(()->controller.replace("r1",List.of()),repository);
    }
    private ScientificCreatorController creators(ScientificCreatorRepository repository) {
        var resources=mock(IDataResourceDao.class);var resource=mock(DataResource.class);
        when(resources.findById("r1")).thenReturn(Optional.of(resource));when(resource.getCreators()).thenReturn(Set.of());
        return new ScientificCreatorController(resources,records,repository,mock(ScientificAffiliationRepository.class),ownership,events, org.mockito.Mockito.mock(edu.kit.datamanager.repo.service.ScientificResourceWriteLock.class));
    }
    @Test public void creatorsMutation() {
        var repository=mock(ScientificCreatorRepository.class);
        mutation(()->creators(repository).replace("r1",List.of()),repository);
    }
    @Test public void relationsVisibility() {
        var repository=mock(ScientificRelationRepository.class);
        reading(()->new ScientificRelationController(repository,records,ownership,events, org.mockito.Mockito.mock(edu.kit.datamanager.repo.service.ScientificResourceWriteLock.class)).list("r1"),repository);
    }
    @Test public void fundingVisibility() {
        var repository=mock(ScientificFundingRepository.class);
        reading(()->new ScientificFundingController(repository,records,ownership,events, org.mockito.Mockito.mock(edu.kit.datamanager.repo.service.ScientificResourceWriteLock.class)).list("r1"),repository);
    }
    @Test public void creatorsVisibility() {
        var repository=mock(ScientificCreatorRepository.class);
        reading(()->creators(repository).list("r1"),repository);
    }
}
