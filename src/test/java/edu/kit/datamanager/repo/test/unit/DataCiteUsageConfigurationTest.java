package edu.kit.datamanager.repo.test.unit;
import edu.kit.datamanager.repo.web.impl.DataCiteUsageConfigurationController;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.util.Optional;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class DataCiteUsageConfigurationTest {
 private final ScientificRecordRepository records=mock(ScientificRecordRepository.class);
 private final IDataResourceDao resources=mock(IDataResourceDao.class);
 @Before public void setup(){var r=new ScientificRecord("r");r.setStatus(PublicationStatus.PUBLISHED);when(records.findById("r")).thenReturn(Optional.of(r));var data=mock(DataResource.class,RETURNS_DEEP_STUBS);when(data.getIdentifier().getValue()).thenReturn("10.1234/EXAMPLE");when(resources.findById("r")).thenReturn(Optional.of(data));}
 private DataCiteUsageConfigurationController controller(boolean enabled,String id,String url){return new DataCiteUsageConfigurationController(records,resources,enabled,id,url);}
 @Test public void productionConfigurationRequiresVisitorConsentAndNeverClaimsCertification(){var response=controller(true,"da-example","https://api.datacite.org").configuration("r");var c=response.getBody();assertTrue(c.enabled());assertTrue(c.consentRequired());assertFalse(c.certified());assertEquals("10.1234/example",c.doi());assertEquals("da-example",c.repositoryId());assertTrue(response.getHeaders().getCacheControl().contains("no-store"));}
 @Test public void disabledTestAndMalformedConfigurationsDoNotExposeTrackingIdentifiers(){for(var c:java.util.List.of(controller(false,"da-example","https://api.datacite.org"),controller(true,"da-example","https://api.test.datacite.org"),controller(true,"account.user","https://api.datacite.org"))){var result=c.configuration("r").getBody();assertFalse(result.enabled());assertEquals("",result.doi());assertEquals("",result.repositoryId());}}
 @Test public void nonPublishedResourceIsNotExposed(){var r=new ScientificRecord("r");r.setStatus(PublicationStatus.DRAFT);when(records.findById("r")).thenReturn(Optional.of(r));var exception=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller(true,"da-example","https://api.datacite.org").configuration("r"));assertEquals(404,exception.getStatusCode().value());verify(resources,never()).findById(anyString());}
 @Test public void testDoiDoesNotTrackEvenOnProductionConfiguration(){var data=mock(DataResource.class,RETURNS_DEEP_STUBS);when(data.getIdentifier().getValue()).thenReturn("10.5072/test");when(resources.findById("r")).thenReturn(Optional.of(data));assertFalse(controller(true,"da-example","https://api.datacite.org").configuration("r").getBody().enabled());}
}
