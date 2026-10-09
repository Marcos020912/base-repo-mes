package edu.kit.datamanager.repo.repository;

import edu.kit.datamanager.repo.domain.UsageObservation;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UsageObservationRepository extends JpaRepository<UsageObservation,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name="jakarta.persistence.lock.timeout",value="2000"))
    @Query("select u from UsageObservation u where u.id=:id")
    Optional<UsageObservation> lock(@Param("id")String id);
    interface Aggregate {String getResourceId();LocalDate getUsageDay();UsageObservation.Kind getKind();Long getRequests();}
    @Query("select u.resourceId as resourceId,u.usageDay as usageDay,u.kind as kind,sum(u.requests) as requests from UsageObservation u, ScientificRecord r where r.resourceId=u.resourceId and r.status=edu.kit.datamanager.repo.domain.PublicationStatus.PUBLISHED and u.usageDay between :start and :end and (:resource is null or u.resourceId=:resource) group by u.resourceId,u.usageDay,u.kind order by u.usageDay,u.resourceId,u.kind")
    List<Aggregate> aggregate(@Param("start")LocalDate start,@Param("end")LocalDate end,@Param("resource")String resource);
    long deleteByUsageDayBefore(LocalDate day);
}
