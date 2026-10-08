package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificFundingRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordEventRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.web.impl.ScientificFundingController;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScientificFundingControllerTest {
    @Test public void onlyAuthorCanReplaceDraftFunding() {
        var funding = mock(ScientificFundingRepository.class);
        var records = mock(ScientificRecordRepository.class);
        var ownership = mock(ResourceOwnershipRepository.class);
        var events = mock(ScientificRecordEventRepository.class);
        var record = new ScientificRecord("r1");
        when(records.findById("r1")).thenReturn(Optional.of(record));
        when(ownership.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "author")));
        when(funding.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        var controller = new ScientificFundingController(funding, records, ownership, events);
        var item = new ScientificFundingController.FundingInput("Agencia", "03yrm5c26", "A-1", "Proyecto");
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author", "", List.of()));
            assertEquals("Agencia", controller.replace("r1", List.of(item)).get(0).getFunderName());
            verify(funding).deleteByResourceId("r1");
            record.setStatus(PublicationStatus.PUBLISHED);
            assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(item)));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other", "", List.of()));
            assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(item)));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test public void invalidRorAndDuplicatesDoNotDeleteExistingFunding() {
        var funding = mock(ScientificFundingRepository.class);
        var records = mock(ScientificRecordRepository.class);
        var ownership = mock(ResourceOwnershipRepository.class);
        when(records.findById("r1")).thenReturn(Optional.of(new ScientificRecord("r1")));
        when(ownership.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "author")));
        var controller = new ScientificFundingController(funding, records, ownership,
                mock(ScientificRecordEventRepository.class));
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author", "", List.of()));
            assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(
                    new ScientificFundingController.FundingInput("Agencia", "not-ror", null, null))));
            var item = new ScientificFundingController.FundingInput("Agencia", null, "P-1", null);
            assertThrows(ResponseStatusException.class, () -> controller.replace("r1", List.of(item, item)));
            verify(funding, never()).deleteByResourceId(any());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test public void sameFunderCanSupportSeparateProjectsWithoutAwardNumbers() {
        var funding = mock(ScientificFundingRepository.class);
        var records = mock(ScientificRecordRepository.class);
        var ownership = mock(ResourceOwnershipRepository.class);
        when(records.findById("r1")).thenReturn(Optional.of(new ScientificRecord("r1")));
        when(ownership.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "author")));
        when(funding.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        var controller = new ScientificFundingController(funding, records, ownership,
                mock(ScientificRecordEventRepository.class));
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("author", "", List.of()));
            assertEquals(2, controller.replace("r1", List.of(
                    new ScientificFundingController.FundingInput("Agencia", null, null, "Proyecto A"),
                    new ScientificFundingController.FundingInput("Agencia", null, null, "Proyecto B"))).size());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
