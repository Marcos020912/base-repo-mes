package edu.kit.datamanager.repo.service;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.time.Instant;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;
public class PublicVersionHistoryServiceTest {
    private ScientificRecordRepository records;private IDataResourceDao resources;private PublicVersionHistoryService service;
    @Before public void setup(){records=mock(ScientificRecordRepository.class);resources=mock(IDataResourceDao.class);service=new PublicVersionHistoryService(records,resources);when(records.findByPreviousResourceId(anyString())).thenReturn(List.of());}
    private ScientificRecord version(String id,String parent,PublicationStatus status,String time){var record=new ScientificRecord(id);record.setPreviousResourceId(parent);record.setStatus(status);record.setVersionLabel(id);record.setPublishedAt(time==null?null:Instant.parse(time));record.setConceptualDoi("10.1234/shared");when(records.findById(id)).thenReturn(Optional.of(record));when(resources.existsById(id)).thenReturn(true);return record;}
    @Test public void includesSiblingsAndWithdrawnButNotDraftOrOrphanAndPaginates(){
        var older=version("older",null,PublicationStatus.PUBLISHED,"2025-01-01T00:00:00Z");var selected=version("selected","older",PublicationStatus.PUBLISHED,"2026-01-01T00:00:00Z");var sibling=version("sibling","older",PublicationStatus.WITHDRAWN,"2026-02-01T00:00:00Z");var draft=version("draft","older",PublicationStatus.DRAFT,null);var orphan=version("orphan","older",PublicationStatus.PUBLISHED,"2026-03-01T00:00:00Z");when(resources.existsById("orphan")).thenReturn(false);
        when(records.findByPreviousResourceId("older")).thenReturn(List.of(selected,sibling,draft,orphan));
        var first=service.list("selected",0,2);assertEquals("selected",first.latestPublishedId());assertFalse(first.newerPublicationAvailable());assertTrue(service.list("older",0,20).newerPublicationAvailable());assertEquals(3,first.total());assertEquals(2,first.pages());assertEquals(List.of("sibling","selected"),first.items().stream().map(PublicVersionHistoryService.Version::id).toList());assertTrue(first.items().get(1).current());assertEquals(PublicationStatus.WITHDRAWN,first.items().get(0).status());assertEquals("older",service.list("selected",1,2).items().get(0).id());
        verify(records,never()).findByPreviousResourceId("draft");verify(records,never()).findByConceptualDoiIgnoreCase(anyString());
    }
    @Test public void cycleTerminatesAndNoDoiBasedUnion(){var a=version("a","b",PublicationStatus.PUBLISHED,null);var b=version("b","a",PublicationStatus.PUBLISHED,null);version("unrelated",null,PublicationStatus.PUBLISHED,null);when(records.findByPreviousResourceId("a")).thenReturn(List.of(b));when(records.findByPreviousResourceId("b")).thenReturn(List.of(a));assertEquals(2,service.list("a",0,20).total());assertNull(service.list("a",0,20).latestPublishedId());assertFalse(service.list("a",0,20).newerPublicationAvailable());verify(records,never()).findByConceptualDoiIgnoreCase(anyString());}
    @Test public void privateRootAndBoundsAreRejected(){version("draft",null,PublicationStatus.DRAFT,null);assertEquals(404,assertThrows(ResponseStatusException.class,()->service.list("draft",0,20)).getStatusCode().value());version("deleted",null,PublicationStatus.PUBLISHED,null);when(resources.existsById("deleted")).thenReturn(false);assertEquals(404,assertThrows(ResponseStatusException.class,()->service.list("deleted",0,20)).getStatusCode().value());assertEquals(400,assertThrows(ResponseStatusException.class,()->service.list("draft",-1,20)).getStatusCode().value());assertEquals(400,assertThrows(ResponseStatusException.class,()->service.list("draft",0,51)).getStatusCode().value());}
}
