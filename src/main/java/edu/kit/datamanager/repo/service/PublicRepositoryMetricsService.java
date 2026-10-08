package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.ScientificCollectionRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inventory metrics, not traffic estimates or COUNTER usage statistics. */
@Service
public class PublicRepositoryMetricsService {
    private final IDataResourceDao resources;
    private final ScientificCollectionRepository collections;
    public PublicRepositoryMetricsService(IDataResourceDao resources, ScientificCollectionRepository collections) {
        this.resources=resources; this.collections=collections;
    }
    private Specification<DataResource> released(String access) {
        return (root,query,cb)->{
            var sq=query.subquery(String.class);
            var science=sq.from(ScientificRecord.class);
            var filter=cb.equal(science.get("status"),PublicationStatus.PUBLISHED);
            if(access!=null)filter=cb.and(filter,cb.equal(science.get("accessLevel"),access));
            sq.select(science.get("resourceId")).where(filter);
            return root.get("id").in(sq);
        };
    }
    public record Metrics(Instant generatedAt,long publishedVersions,long openPolicyVersions,long publishedCollections,
                          Map<String,String> definitions,String scope) {}
    @Transactional(readOnly=true)
    public Metrics snapshot() {
        return new Metrics(Instant.now(),resources.count(released(null)),resources.count(released("OPEN")),
                collections.count((root,query,cb)->cb.isTrue(root.get("published"))),
                Map.of("publishedVersions","Versiones existentes en estado PUBLISHED; cada versión cuenta una vez. Excluye borradores y retiradas.",
                       "openPolicyVersions","Versiones publicadas cuya política declarada es OPEN. No acredita descarga efectiva ni cumplimiento de embargo.",
                       "publishedCollections","Colecciones marcadas como públicas, incluidas las que no tienen versiones visibles."),
                "Inventario actual; no son visitas, descargas, usuarios únicos ni estadísticas certificadas COUNTER/DataCite.");
    }
}
