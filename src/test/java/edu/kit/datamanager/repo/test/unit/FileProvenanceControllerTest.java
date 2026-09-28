package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.repository.FileProvenanceEventRepository;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.web.impl.FileProvenanceController;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FileProvenanceControllerTest {
    @Test public void historyIsPrivateToAuthorAndCurator() {
        var events = mock(FileProvenanceEventRepository.class);
        var ownership = mock(ResourceOwnershipRepository.class);
        var resources = mock(IDataResourceDao.class);
        when(resources.existsById("r1")).thenReturn(true);
        when(ownership.findById("r1")).thenReturn(Optional.of(new ResourceOwnership("r1", "owner")));
        when(events.findByResourceIdOrderByOccurredAtDescIdDesc(eq("r1"), any())).thenReturn(Page.empty());
        var controller = new FileProvenanceController(events, ownership, resources);
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other", "", List.of()));
            assertThrows(ResponseStatusException.class, () -> controller.list("r1", 0, 10));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", "", List.of()));
            assertTrue(controller.list("r1", 0, 10).isEmpty());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("curator", "",
                    List.of(new SimpleGrantedAuthority("ROLE_CURATOR"))));
            assertTrue(controller.list("r1", 0, 10).isEmpty());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
