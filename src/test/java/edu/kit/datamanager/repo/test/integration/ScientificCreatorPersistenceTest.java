package edu.kit.datamanager.repo.test.integration;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.ScientificCreator;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.web.impl.ScientificCreatorController;
import java.time.Instant;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ScientificCreatorPersistenceTest {
    @Autowired private IDataResourceDao resources;
    @Autowired private ScientificRecordRepository records;
    @Autowired private ResourceOwnershipRepository ownership;
    @Autowired private ScientificCreatorRepository creators;
    @Autowired private ScientificCreatorController controller;

    @After public void clear() { SecurityContextHolder.clearContext(); }

    @Test @Transactional public void replaceFlushesUniqueKeysAndPreservesOnlyMatchingAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana", "", List.of()));
        DataResource resource = DataResource.factoryNewDataResource("orcid-replace-persistence");
        resource.getCreators().add(Agent.factoryAgent("Ana", "Pérez", new String[]{}));
        resource = resources.saveAndFlush(resource);
        String id = resource.getId();
        Long creatorId = resource.getCreators().iterator().next().getId();
        records.saveAndFlush(new ScientificRecord(id));
        ownership.saveAndFlush(new ResourceOwnership(id, "ana"));
        ScientificCreator initial = new ScientificCreator(id, creatorId, null, null, null);
        initial.authenticateOrcid("0000-0002-1825-0097", "ana", Instant.now());
        creators.saveAndFlush(initial);
        var same = controller.replace(id, List.of(new ScientificCreatorController.CreatorInput(
                creatorId, "0000-0002-1825-0097", "Instituto A", null)));
        assertTrue(same.get(0).orcidAuthenticated());
        var changed = controller.replace(id, List.of(new ScientificCreatorController.CreatorInput(
                creatorId, "0000-0003-2804-688X", "Instituto A", null)));
        assertFalse(changed.get(0).orcidAuthenticated());
        assertEquals(1, creators.findByResourceId(id).size());
    }
}
