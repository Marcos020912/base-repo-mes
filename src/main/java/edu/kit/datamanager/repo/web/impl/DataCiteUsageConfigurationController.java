package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Public non-secret configuration; no user data is forwarded by the server. */
@RestController
public class DataCiteUsageConfigurationController {
 private final ScientificRecordRepository records;
 private final IDataResourceDao resources;
 private final boolean enabled;
 private final String repositoryId,api;
 public DataCiteUsageConfigurationController(ScientificRecordRepository records,IDataResourceDao resources,
   @Value("${repo.metrics.datacite.enabled:false}")boolean enabled,
   @Value("${repo.metrics.datacite.repository-id:}")String repositoryId,
   @Value("${repo.datacite.api-url:https://api.test.datacite.org}")String api){
  this.records=records;this.resources=resources;this.enabled=enabled;this.repositoryId=repositoryId;this.api=api;
 }
 @io.swagger.v3.oas.annotations.media.Schema(name="DataCiteUsageConfiguration")
 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
 public record Configuration(boolean enabled,String repositoryId,String doi,boolean consentRequired,boolean certified){}
 @GetMapping("/api/v1/public/resources/{id}/usage-configuration") @Transactional(readOnly=true)
 public ResponseEntity<Configuration> configuration(@PathVariable String id){
  if(records.findById(id).filter(r->r.getStatus()==PublicationStatus.PUBLISHED).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  var resource=resources.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  String doi=resource.getIdentifier()==null?"":java.util.Objects.toString(resource.getIdentifier().getValue(),"").toLowerCase(Locale.ROOT);
  boolean active=enabled&&repositoryId.matches("da-[A-Za-z0-9_-]{1,100}")&&api.replaceAll("/+$","").equals("https://api.datacite.org")&&doi.matches("10\\.\\d{4,9}/[^\\s?#]{1,220}")&&!doi.startsWith("10.5072/");
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Configuration(active,active?repositoryId:"",active?doi:"",true,false));
 }
}
