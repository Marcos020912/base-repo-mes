package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificAffiliationRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.web.impl.ScientificCreatorController;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ScientificCreatorControllerTest {
    private final IDataResourceDao resources = mock(IDataResourceDao.class);
    private final ScientificRecordRepository records = mock(ScientificRecordRepository.class);
    private final ScientificCreatorRepository creators = mock(ScientificCreatorRepository.class);
    private final ScientificAffiliationRepository affiliations = mock(ScientificAffiliationRepository.class);
    private final ResourceOwnershipRepository ownership = mock(ResourceOwnershipRepository.class);
    private final ScientificRecordEventRepository events = mock(ScientificRecordEventRepository.class);
    private final ScientificRecord science = new ScientificRecord("r1");
    private ScientificCreatorController controller;

    @Before public void setup() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ana", "", List.of()));
        Agent ana = mock(Agent.class); when(ana.getId()).thenReturn(1L); when(ana.getGivenName()).thenReturn("Ana");
        Agent luis = mock(Agent.class); when(luis.getId()).thenReturn(2L); when(luis.getGivenName()).thenReturn("Luis");
        DataResource resource = mock(DataResource.class); when(resource.getCreators()).thenReturn(Set.of(ana, luis));
        when(resources.findById("r1")).thenReturn(Optional.of(resource));
        when(records.findById("r1")).thenReturn(Optional.of(science));
        ResourceOwnership owner = mock(ResourceOwnership.class); when(owner.getUsername()).thenReturn("ana");
        when(ownership.findById("r1")).thenReturn(Optional.of(owner));
        when(creators.findByResourceId("r1")).thenReturn(List.of());
        when(affiliations.findByResourceIdOrderByCreatorIdAscSortOrderAsc("r1")).thenReturn(List.of());
        controller = new ScientificCreatorController(resources, records, creators, affiliations, ownership, events);
    }

    @After public void clear() { SecurityContextHolder.clearContext(); }

    @Test public void acceptsExactCreatorsAndRejectsUnknownOrIncompleteList() {
        assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(
                new ScientificCreatorController.CreatorInput(1L, "0000-0003-2804-688X", "Universidad A", "03yrm5c26"))));
        assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(
                new ScientificCreatorController.CreatorInput(1L, null, null, null),
                new ScientificCreatorController.CreatorInput(3L, null, null, null))));
        controller.replace("r1", List.of(
                new ScientificCreatorController.CreatorInput(1L, "0000-0003-2804-688X", "Universidad A", "03yrm5c26"),
                new ScientificCreatorController.CreatorInput(2L, null, "Universidad B", null)));
        verify(creators).saveAll(argThat(values -> {
            int count = 0; for (var ignored : values) count++; return count == 2;
        }));
    }

    @Test public void publishedOrNonOwnerCannotEdit() {
        science.setStatus(PublicationStatus.PUBLISHED);
        assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of()));
        science.setStatus(PublicationStatus.DRAFT);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("otro", "", List.of()));
        assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of()));
        verify(creators, never()).deleteByResourceId(anyString());
    }

    @Test public void keepsTwoDistinctInstitutionsForOneCreatorAndRejectsDuplicates() {
        var first = new ScientificCreatorController.AffiliationInput("Universidad A", "03yrm5c26");
        var second = new ScientificCreatorController.AffiliationInput("Instituto B", null);
        var valid = List.of(
                new ScientificCreatorController.CreatorInput(1L, "0000-0003-2804-688X", null, null, List.of(first, second)),
                new ScientificCreatorController.CreatorInput(2L, null, null, null, List.of()));
        controller.replace("r1", valid);
        verify(affiliations).saveAll(argThat(values -> {
            int count = 0; for (var item : values) {
                assertEquals(1L, item.getCreatorId().longValue());
                assertEquals(count++, item.getSortOrder());
            }
            return count == 2;
        }));
        assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(
                new ScientificCreatorController.CreatorInput(1L, null, null, null, List.of(first, first)),
                new ScientificCreatorController.CreatorInput(2L, null, null, null, List.of()))));
    }

    @Test public void legacySingleInstitutionAppearsAsOneAffiliation() {
        when(creators.findByResourceId("r1")).thenReturn(List.of(
                new ScientificCreator("r1", 1L, null, "Universidad histórica", "03yrm5c26")));
        var result = controller.list("r1");
        var ana = result.stream().filter(item -> item.creatorId() == 1L).findFirst().orElseThrow();
        assertEquals(1, ana.affiliations().size());
        assertEquals("Universidad histórica", ana.affiliations().get(0).institution());
    }
}
