package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Author-scoped task projection. Inspection is performed only for the requested page. */
@Service
@Transactional(readOnly=true)
public class DepositTaskService {
    private final ResourceOwnershipRepository ownership;
    private final IDataResourceDao resources;
    private final ScientificRecordRepository records;
    private final ScientificQualityService quality;
    public DepositTaskService(ResourceOwnershipRepository ownership,IDataResourceDao resources,ScientificRecordRepository records,ScientificQualityService quality) {
        this.ownership=ownership;this.resources=resources;this.records=records;this.quality=quality;
    }
    @io.swagger.v3.oas.annotations.media.Schema(name="DepositTask")
    public record Task(String resourceId,String title,PublicationStatus status,int completionPercent,List<String> blockers,List<ScientificQualityService.QualityCheck> pending,String nextAction) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="DepositTasksPage")
    public record Tasks(List<Task> items,int page,int size,long total,int pages) {}
    public Tasks mine(int page,int size) {
        if(page<0||size<1||size>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginación no válida.");
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null||!authentication.isAuthenticated()||"anonymousUser".equals(authentication.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inicie sesión.");
        var owned=ownership.findByUsernameIgnoreCaseOrderByCreatedAtDesc(authentication.getName());
        var ids=owned.stream().map(item->item.getResourceId()).toList();
        Map<String,ScientificRecord> science=new HashMap<>();records.findAllById(ids).forEach(item->science.put(item.getResourceId(),item));
        Map<String,edu.kit.datamanager.repo.domain.DataResource> data=new HashMap<>();resources.findAllById(ids).forEach(item->data.put(item.getId(),item));
        var active=ids.stream().filter(data::containsKey).filter(id->{var s=science.get(id);return s==null||s.getStatus()==PublicationStatus.DRAFT||s.getStatus()==PublicationStatus.IN_REVIEW;}).toList();
        long offset=(long)page*size;
        var items=active.stream().skip(offset).limit(size).map(id->{
            var s=science.getOrDefault(id,new ScientificRecord(id));var report=quality.inspect(s);var r=data.get(id);
            String title=r.getTitles()==null?"Sin título":r.getTitles().stream().map(t->t.getValue()).filter(t->t!=null&&!t.isBlank()).findFirst().orElse("Sin título");
            var pending=report.checks().stream().filter(check->!check.complete()).toList();
            String action=s.getStatus()==PublicationStatus.IN_REVIEW?"Esperar revisión de curación":report.blockers().isEmpty()?"Revisar ficha y enviar a curación":"Completar metadatos y documentación";
            return new Task(id,title,s.getStatus(),report.completionPercent(),report.blockers(),pending,action);
        }).toList();
        return new Tasks(items,page,size,active.size(),(int)((active.size()+(long)size-1)/size));
    }
}
