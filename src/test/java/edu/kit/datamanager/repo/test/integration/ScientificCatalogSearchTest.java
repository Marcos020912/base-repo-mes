package edu.kit.datamanager.repo.test.integration;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ResourceType;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.Title;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.web.impl.ScientificCatalogController;
import edu.kit.datamanager.repo.web.impl.PublicScientificResourceController;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ScientificCatalogSearchTest {
    @Autowired private ScientificCatalogController catalog;
    @Autowired private IDataResourceDao resources;
    @Autowired private ScientificRecordRepository records;
    @Autowired private PublicScientificResourceController publicResources;

    @Test
    @Transactional
    public void publicSearchOnlyReturnsReleasedOpenResources() {
        DataResource data = DataResource.factoryNewDataResource("catalog-test");
        data.getTitles().add(Title.factoryTitle("Datos de astronomía", Title.TYPE.OTHER));
        data.getCreators().add(Agent.factoryAgent("Ada", "López", new String[]{"Universidad"}));
        data.setPublisher("Universidad"); data.setPublicationYear("2026");
        data.setResourceType(ResourceType.createResourceType("dataset", ResourceType.TYPE_GENERAL.DATASET));
        data = resources.save(data);
        ScientificRecord record = records.saveAndFlush(new ScientificRecord(data.getId()));
        MockHttpServletRequest publicRequest = new MockHttpServletRequest("GET", "/api/v1/public/catalog");
        var before = catalog.list("astronomía", "Ada", "DATASET", "2026", "", "", "", "", "", "", false, false, "newest", 0, 20, publicRequest);
        assertEquals(0, before.total());
        MockHttpServletRequest internalRequest = new MockHttpServletRequest("GET", "/api/v1/catalog");
        assertEquals(0, catalog.list("astronomía", "Ada", "DATASET", "2026", "", "", "", "", "", "", false, false, "newest", 0, 20, internalRequest).total());
        record.setStatus(PublicationStatus.PUBLISHED); record.setAccessLevel("OPEN");
        record.setVersionDoi("10.1234/astro"); record.setLicenseId("CC-BY-4.0"); record.setDiscipline("Astronomía");
        record.setInstitution("Universidad"); record.setLanguage("es");
        records.saveAndFlush(record);
        var after = catalog.list("astronomía", "Ada", "DATASET", "2026", "CC-BY-4.0", "astro", "", "", "", "", true, false, "newest", 0, 20, publicRequest);
        assertEquals(1, after.total());
        assertEquals(data.getId(), after.items().get(0).id());
        record.setAccessLevel("RESTRICTED"); records.saveAndFlush(record);
        var restricted = catalog.list("astronomía", "Ada", "DATASET", "2026", "", "", "", "", "", "", false, false, "newest", 0, 20, publicRequest);
        assertEquals(1, restricted.total());
        assertEquals("RESTRICTED", restricted.items().get(0).accessLevel());
        var filtered = catalog.list("astronomía", "Ada", "DATASET", "2026", "", "", "Universidad", "es", "RESTRICTED", "", false, false, "year_desc", 0, 20, publicRequest);
        assertEquals(1, filtered.total());
        var facets = catalog.facets("astronomía", "Ada", "", "", "", "", "", "", "", "", false, false, publicRequest);
        assertTrue(facets.get("type").stream().anyMatch(option -> option.value().equals("DATASET") && option.count() >= 1));
        assertTrue(facets.get("author").stream().anyMatch(option -> option.value().trim().equals("Ada López") && option.count() >= 1));
        assertTrue(facets.get("access").stream().anyMatch(option -> option.value().equals("RESTRICTED") && option.count() >= 1));
        assertTrue(facets.get("year").stream().anyMatch(option -> option.value().equals("2026") && option.count() >= 1));
        assertEquals(0, catalog.list("astronomía", "Ada", "DATASET", "2026", "", "", "", "", "", "", false, true, "newest", 0, 20, publicRequest).total());
        assertEquals(1, catalog.list("", "Ada López", "DATASET", "2026", "", "", "", "", "", "", false, false, "newest", 0, 20, publicRequest).total());
        assertEquals(0, catalog.list("astronomía", "Ada", "DATASET", "2026", "", "", "", "", "", "csv", false, false, "newest", 0, 20, publicRequest).total());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                catalog.list("", "", "", "", "", "", "", "", "", "", false, false, "arbitrary", 0, 20, publicRequest));
    }

    @Test
    @Transactional
    public void olderLandingLinksToPublishedSuccessor() throws Exception {
        DataResource first = DataResource.factoryNewDataResource("catalog-version-one");
        first.getTitles().add(Title.factoryTitle("Versión inicial", Title.TYPE.OTHER));
        first = resources.save(first);
        ScientificRecord firstRecord = new ScientificRecord(first.getId());
        firstRecord.setStatus(PublicationStatus.PUBLISHED); records.saveAndFlush(firstRecord);
        DataResource second = DataResource.factoryNewDataResource("catalog-version-two");
        second.getTitles().add(Title.factoryTitle("Versión posterior", Title.TYPE.OTHER));
        second = resources.save(second);
        ScientificRecord next = new ScientificRecord(second.getId());
        next.setPreviousResourceId(first.getId()); next.setStatus(PublicationStatus.PUBLISHED);
        records.saveAndFlush(next);
        var landing = (PublicScientificResourceController.PublicDetail) publicResources.detail(first.getId()).getBody();
        assertEquals(second.getId(), landing.newerVersionId());
    }
}
