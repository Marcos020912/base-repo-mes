package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Public lineage uses explicit version links, never coincidentally matching DOI strings. */
@Service
@Transactional(readOnly=true)
public class PublicVersionHistoryService {
    private final ScientificRecordRepository records;private final IDataResourceDao resources;
    public PublicVersionHistoryService(ScientificRecordRepository records,IDataResourceDao resources){this.records=records;this.resources=resources;}
    @io.swagger.v3.oas.annotations.media.Schema(name="PublicScientificVersion")
    public record Version(String id,String version,String doi,String conceptualDoi,PublicationStatus status,Instant publishedAt,boolean current){}
    @io.swagger.v3.oas.annotations.media.Schema(name="PublicVersionHistoryPage")
    public record History(List<Version> items,int page,int size,long total,int pages,String latestPublishedId,boolean newerPublicationAvailable){}
    private boolean visible(ScientificRecord record){return (record.getStatus()==PublicationStatus.PUBLISHED||record.getStatus()==PublicationStatus.WITHDRAWN)&&resources.existsById(record.getResourceId());}
    public History list(String id,int page,int size){
        if(page<0||size<1||size>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginación no válida.");
        var root=records.findById(id).filter(this::visible).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<String,ScientificRecord> family=new LinkedHashMap<>();Set<String> visited=new HashSet<>();Deque<ScientificRecord> queue=new ArrayDeque<>();queue.add(root);
        while(!queue.isEmpty()){
            var record=queue.remove();if(!visited.add(record.getResourceId()))continue;
            if(visited.size()>500)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo consultar la familia completa de versiones.");
            if(!visible(record))continue;family.put(record.getResourceId(),record);
            if(record.getPreviousResourceId()!=null)records.findById(record.getPreviousResourceId()).filter(this::visible).ifPresent(queue::add);
            records.findByPreviousResourceId(record.getResourceId()).stream().filter(this::visible).forEach(queue::add);
        }
        var ordered=family.values().stream().sorted(Comparator.comparing(ScientificRecord::getPublishedAt,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(ScientificRecord::getResourceId)).toList();
        var items=ordered.stream().skip((long)page*size).limit(size).map(record->new Version(record.getResourceId(),record.getVersionLabel(),record.getVersionDoi(),record.getConceptualDoi(),record.getStatus(),record.getPublishedAt(),id.equals(record.getResourceId()))).toList();
        var latest=ordered.stream().filter(record->record.getStatus()==PublicationStatus.PUBLISHED&&record.getPublishedAt()!=null).findFirst().orElse(null);
        boolean newer=latest!=null&&root.getPublishedAt()!=null&&latest.getPublishedAt().isAfter(root.getPublishedAt());
        return new History(items,page,size,ordered.size(),(int)((ordered.size()+(long)size-1)/size),latest==null?null:latest.getResourceId(),newer);
    }
}
