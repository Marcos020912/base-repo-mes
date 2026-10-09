package edu.kit.datamanager.repo.service;
import edu.kit.datamanager.repo.domain.RepositorySelfAssessment;
import edu.kit.datamanager.repo.repository.RepositorySelfAssessmentRepository;
import java.time.Instant;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class RepositorySelfAssessmentService {
 public static final String SOURCE="https://zenodo.org/records/17660463";
 private static final List<String> IDS=java.util.stream.IntStream.rangeClosed(1,16).mapToObj(i->String.format(java.util.Locale.ROOT,"R%02d",i)).toList();
 private final RepositorySelfAssessmentRepository repository;
 public RepositorySelfAssessmentService(RepositorySelfAssessmentRepository repository){this.repository=repository;}
 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
 public record Item(String id,Long revision,RepositorySelfAssessment.State state,String responsible,String statement,List<String> evidence,String updatedBy,Instant updatedAt){}
 @io.swagger.v3.oas.annotations.media.Schema(name="RepositorySelfAssessmentReport")
 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
 public record Report(String schema,String requirementsVersion,String source,Instant generatedAt,boolean certified,List<Item> items){}
 @Transactional(readOnly=true) public Report report(){
  Map<String,RepositorySelfAssessment> entries=new HashMap<>();repository.findAll().forEach(e->entries.put(e.getRequirementId(),e));
  return new Report("reduniv.repository-self-assessment.v1","2026-2028-v01.00",SOURCE,Instant.now(),false,IDS.stream().map(id->view(id,entries.get(id))).toList());
 }
 @Transactional public Item save(String id,Long revision,RepositorySelfAssessment.State state,String responsible,String statement,List<String> evidence,String actor){
  if(!IDS.contains(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  var current=repository.findById(id).orElse(null);
  if(current==null?revision!=null:revision==null||revision!=current.getRevision())throw new ResponseStatusException(HttpStatus.CONFLICT,"La evaluación cambió; recargue antes de guardar.");
  if(state==null)throw bad();
  String owner=clean(responsible,255),description=clean(statement,5000);
  if(evidence==null||evidence.size()>20)throw bad();
  var links=new LinkedHashSet<String>();
  for(String link:evidence){
   if(link==null||link.length()>1000)throw bad();
   try{URI uri=URI.create(link.trim());if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null)throw bad();links.add(uri.toString());}
   catch(IllegalArgumentException failure){throw bad();}
  }
  String serialized=String.join("\n",links);if(serialized.length()>4000)throw bad();
  if((state==RepositorySelfAssessment.State.READY_FOR_REVIEW||state==RepositorySelfAssessment.State.INTERNALLY_REVIEWED)&&(owner==null||description==null||links.isEmpty()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indique responsable, declaración y enlaces de evidencia antes de marcarla preparada o revisada.");
  if(current==null)current=new RepositorySelfAssessment(id);
  current.setState(state);current.setResponsible(owner);current.setStatement(description);current.setEvidence(serialized);current.setUpdatedBy(actor);current.setUpdatedAt(Instant.now());
  return view(id,repository.saveAndFlush(current));
 }
 private static Item view(String id,RepositorySelfAssessment e){return e==null?new Item(id,null,RepositorySelfAssessment.State.NOT_STARTED,null,null,List.of(),null,null):new Item(id,e.getRevision(),e.getState(),e.getResponsible(),e.getStatement(),e.getEvidence()==null||e.getEvidence().isBlank()?List.of():List.of(e.getEvidence().split("\n")),e.getUpdatedBy(),e.getUpdatedAt());}
 private static String clean(String value,int max){if(value==null||value.isBlank())return null;if(value.length()>max)throw bad();return value.trim();}
 private static ResponseStatusException bad(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Datos de autoevaluación no válidos. Use enlaces HTTPS sin credenciales ni parámetros privados.");}
}
