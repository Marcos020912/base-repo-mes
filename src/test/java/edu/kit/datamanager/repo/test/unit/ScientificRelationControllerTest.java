package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.ScientificRelation;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.repository.ScientificRelationRepository;
import edu.kit.datamanager.repo.web.impl.ScientificRelationController;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ScientificRelationControllerTest {
    @Test public void authorCanReplaceDraftRelationsButNotPublishedOnes() {
        var relations = mock(ScientificRelationRepository.class);
        var records = mock(ScientificRecordRepository.class);
        var ownership = mock(ResourceOwnershipRepository.class);
        var events = mock(ScientificRecordEventRepository.class);
        var record = new ScientificRecord("r1");
        when(records.findById("r1")).thenReturn(Optional.of(record));
        when(ownership.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "author")));
        when(relations.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        var controller = new ScientificRelationController(relations, records, ownership, events);
        var input = new ScientificRelationController.RelationInput(ScientificRelation.Kind.ARTICLE,
                ScientificRelation.IdentifierType.DOI, ScientificRelation.RelationType.IsReferencedBy,
                "https://doi.org/10.1234/article", "Artículo asociado");
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author", "", List.of()));
            var saved = controller.replace("r1", List.of(input));
            assertEquals("10.1234/article", saved.get(0).getIdentifier());
            verify(relations).deleteByResourceId("r1");
            record.setStatus(edu.kit.datamanager.repo.domain.PublicationStatus.PUBLISHED);
            assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(input)));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other", "", List.of()));
            assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(input)));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test public void rejectsInvalidAndDuplicateRelationsBeforeDeletion() {
        var relations = mock(ScientificRelationRepository.class);
        var records = mock(ScientificRecordRepository.class);
        var ownership = mock(ResourceOwnershipRepository.class);
        when(records.findById("r1")).thenReturn(Optional.of(new ScientificRecord("r1")));
        when(ownership.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "author")));
        var controller = new ScientificRelationController(relations, records, ownership, mock(ScientificRecordEventRepository.class));
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author", "", List.of()));
            var bad = new ScientificRelationController.RelationInput(ScientificRelation.Kind.SOFTWARE,
                    ScientificRelation.IdentifierType.URL, ScientificRelation.RelationType.IsDocumentedBy,
                    "javascript:alert(1)", null);
            assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(bad)));
            var good = new ScientificRelationController.RelationInput(ScientificRelation.Kind.ARTICLE,
                    ScientificRelation.IdentifierType.DOI, ScientificRelation.RelationType.IsReferencedBy,
                    "10.1234/article", null);
            assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(good, good)));
            verify(relations, never()).deleteByResourceId(any());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
