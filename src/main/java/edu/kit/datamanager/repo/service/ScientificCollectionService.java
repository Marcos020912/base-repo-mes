package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.repository.*;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class ScientificCollectionService {
    private final ScientificCollectionRepository collections;
    private final ScientificCollectionMemberRepository members;
    private final ScientificRecordRepository records;
    private final IDataResourceDao resources;
    public ScientificCollectionService(ScientificCollectionRepository collections,ScientificCollectionMemberRepository members,ScientificRecordRepository records,IDataResourceDao resources) {
        this.collections=collections;this.members=members;this.records=records;this.resources=resources;
    }
    private PageRequest paging(int page,int size,String sort) {
        if(page<0 || page>100000 || size<1 || size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginación no válida.");
        return PageRequest.of(page,size,Sort.by(sort,"id"));
    }
    private ScientificCollection require(String id,boolean publicOnly) {
        var collection=collections.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Colección no encontrada."));
        if(publicOnly && !collection.isPublished()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Colección no encontrada.");
        return collection;
    }
    private Specification<DataResource> membership(String id,boolean publicOnly) {
        return (root,query,cb)->{
            var memberQuery=query.subquery(String.class);
            var member=memberQuery.from(ScientificCollectionMember.class);
            memberQuery.select(member.get("resourceId")).where(cb.equal(member.get("collectionId"),id));
            var inCollection=root.get("id").in(memberQuery);
            if(!publicOnly) return inCollection;
            var publishedQuery=query.subquery(String.class);
            var science=publishedQuery.from(ScientificRecord.class);
            publishedQuery.select(science.get("resourceId")).where(cb.equal(science.get("status"),PublicationStatus.PUBLISHED));
            return cb.and(inCollection,root.get("id").in(publishedQuery));
        };
    }
    public record CollectionView(String id,long revision,String title,String description,ScientificCollection.Kind kind,boolean published,long datasets) {}
    public record CollectionPage(List<CollectionView> items,long total,int page,int pages) {}
    public record DatasetView(String id,String title,List<String> authors,String type,String year,String status) {}
    public record DatasetPage(CollectionView collection,List<DatasetView> items,long total,int page,int pages) {}
    private CollectionView view(ScientificCollection c,boolean publicOnly) {
        return new CollectionView(c.getId(),c.getRevision(),c.getTitle(),c.getDescription(),c.getKind(),c.isPublished(),resources.count(membership(c.getId(),publicOnly)));
    }
    @Transactional(readOnly=true)
    public CollectionPage list(boolean publicOnly,String kind,int page,int size) {
        ScientificCollection.Kind category;
        try { category=kind.isBlank()?null:ScientificCollection.Kind.valueOf(kind); }
        catch(IllegalArgumentException e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Categoría de colección no válida.");}
        Specification<ScientificCollection> filter=(root,query,cb)->{
            var visible=publicOnly?cb.isTrue(root.get("published")):cb.conjunction();
            return category==null?visible:cb.and(visible,cb.equal(root.get("kind"),category));
        };
        var found=collections.findAll(filter,paging(page,size,"title"));
        return new CollectionPage(found.stream().map(c->view(c,publicOnly)).toList(),found.getTotalElements(),found.getNumber(),found.getTotalPages());
    }
    @Transactional(readOnly=true)
    public DatasetPage datasets(String id,boolean publicOnly,int page,int size) {
        var collection=require(id,publicOnly);
        var found=resources.findAll(membership(id,publicOnly),paging(page,size,"lastUpdate"));
        var items=found.stream().map(r->new DatasetView(r.getId(),r.getTitles().stream().findFirst().map(Title::getValue).orElse("Sin título"),
            r.getCreators().stream().map(a->String.join(" ",a.getGivenName()==null?"":a.getGivenName(),a.getFamilyName()==null?"":a.getFamilyName()).trim()).toList(),
            r.getResourceType()==null?null:r.getResourceType().getTypeGeneral().name(),r.getPublicationYear(),
            records.findById(r.getId()).map(s->s.getStatus().name()).orElse("DRAFT"))).toList();
        return new DatasetPage(view(collection,publicOnly),items,found.getTotalElements(),found.getNumber(),found.getTotalPages());
    }
    public CollectionView create(String title,String description,ScientificCollection.Kind kind,boolean published) {
        return view(collections.saveAndFlush(new ScientificCollection(title.trim(),description,kind,published)),false);
    }
    public CollectionView update(String id,long revision,String title,String description,ScientificCollection.Kind kind,boolean published) {
        var c=require(id,false);
        if(c.getRevision()!=revision) throw new ResponseStatusException(HttpStatus.CONFLICT,"La colección cambió. Actualiza la página antes de editar.");
        c.setTitle(title.trim());c.setDescription(description);c.setKind(kind);c.setPublished(published);
        return view(collections.saveAndFlush(c),false);
    }
    public void add(String id,String resourceId) {
        require(id,false);
        if(!resources.existsById(resourceId) || !records.existsById(resourceId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Dataset no encontrado.");
        if(!members.existsByCollectionIdAndResourceId(id,resourceId)) members.saveAndFlush(new ScientificCollectionMember(id,resourceId));
    }
    public void remove(String id,String resourceId) {require(id,false);members.deleteByCollectionIdAndResourceId(id,resourceId);}
    public void delete(String id) {var c=require(id,false);members.deleteByCollectionId(id);collections.delete(c);}
}
