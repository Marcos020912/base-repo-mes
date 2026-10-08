package edu.kit.datamanager.repo.test.integration;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.service.ScientificCollectionService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
public class ScientificCollectionsTest {
    @Autowired private ScientificCollectionService collections;
    @Autowired private IDataResourceDao resources;
    @Autowired private ScientificRecordRepository records;
    private String dataset(PublicationStatus status) {
        var resource=DataResource.factoryNewDataResource(java.util.UUID.randomUUID().toString());
        resource.getTitles().add(Title.factoryTitle("Dataset de colección",Title.TYPE.OTHER));
        resource.setPublisher("Prueba");resource.setPublicationYear("2026");
        resource.setResourceType(ResourceType.createResourceType("dataset",ResourceType.TYPE_GENERAL.DATASET));
        resource=resources.saveAndFlush(resource);
        var record=new ScientificRecord(resource.getId());record.setStatus(status);records.saveAndFlush(record);
        return resource.getId();
    }
    @Test public void publishedCollectionExcludesDraftAndWithdrawnMembers() {
        var c=collections.create("Astronomía","Descripción",ScientificCollection.Kind.THEMATIC,true);
        String released=dataset(PublicationStatus.PUBLISHED),draft=dataset(PublicationStatus.DRAFT),withdrawn=dataset(PublicationStatus.WITHDRAWN);
        for(String id:java.util.List.of(released,draft,withdrawn))collections.add(c.id(),id);
        var publicPage=collections.datasets(c.id(),true,0,20);
        assertEquals(1,publicPage.total());assertEquals(released,publicPage.items().get(0).id());assertEquals(1,publicPage.collection().datasets());
        assertEquals(3,collections.datasets(c.id(),false,0,20).total());
        records.findById(released).orElseThrow().setStatus(PublicationStatus.WITHDRAWN);records.flush();
        assertEquals(0,collections.datasets(c.id(),true,0,20).total());
    }
    @Test public void privateCollectionIsNotPublicEvenWithPublishedDataset() {
        var c=collections.create("Privada","",ScientificCollection.Kind.INSTITUTIONAL,false);
        collections.add(c.id(),dataset(PublicationStatus.PUBLISHED));
        assertFalse(collections.list(true,"",0,100).items().stream().anyMatch(item->item.id().equals(c.id())));
        assertThrows(ResponseStatusException.class,()->collections.datasets(c.id(),true,0,20));
    }
    @Test public void membershipIsIdempotentAndDeleteNeverDeletesDataset() {
        String id=dataset(PublicationStatus.PUBLISHED);
        var one=collections.create("Uno","",ScientificCollection.Kind.THEMATIC,true);
        var two=collections.create("Dos","",ScientificCollection.Kind.INSTITUTIONAL,true);
        collections.add(one.id(),id);collections.add(one.id(),id);collections.add(two.id(),id);
        assertEquals(1,collections.datasets(one.id(),true,0,20).total());
        collections.remove(one.id(),id);assertTrue(resources.existsById(id));
        collections.add(one.id(),id);collections.delete(one.id());
        assertTrue(resources.existsById(id));assertEquals(1,collections.datasets(two.id(),true,0,20).total());
    }
    @Test public void serverPaginationAndRevisionConflictAreEnforced() {
        var c=collections.create("Paginada","",ScientificCollection.Kind.THEMATIC,true);
        collections.add(c.id(),dataset(PublicationStatus.PUBLISHED));collections.add(c.id(),dataset(PublicationStatus.PUBLISHED));
        assertEquals(1,collections.datasets(c.id(),true,0,1).items().size());
        assertEquals(2,collections.datasets(c.id(),true,0,1).pages());
        assertEquals(1,collections.datasets(c.id(),true,1,1).items().size());
        collections.update(c.id(),c.revision(),"Nuevo título","",c.kind(),true);
        assertThrows(ResponseStatusException.class,()->collections.update(c.id(),c.revision(),"Obsoleto","",c.kind(),true));
        assertThrows(ResponseStatusException.class,()->collections.list(true,"INVALID",0,20));
        assertThrows(ResponseStatusException.class,()->collections.datasets(c.id(),true,-1,20));
    }
}
