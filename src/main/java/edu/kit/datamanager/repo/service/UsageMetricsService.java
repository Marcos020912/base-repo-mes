package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class UsageMetricsService {
    private final UsageObservationRepository observations;
    private final ScientificRecordRepository records;
    private final String secret;
    private final boolean enabled;
    private final jakarta.persistence.EntityManager entities;
    private final boolean postgres;
    public UsageMetricsService(UsageObservationRepository observations,ScientificRecordRepository records,
            @Value("${repo.metrics.privacy-secret:${repo.auth.jwtSecret:}}")String secret,
            @Value("${repo.metrics.usage-enabled:true}")boolean enabled, jakarta.persistence.EntityManager entities,
            org.springframework.boot.autoconfigure.jdbc.DataSourceProperties database) {
        this.observations=observations;this.records=records;this.secret=secret;this.enabled=enabled;this.entities=entities;this.postgres=database.determineUrl().startsWith("jdbc:postgresql:");
    }
    public boolean enabled(){return enabled&&secret!=null&&secret.length()>=32;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=5)
    public void record(String resource,UsageObservation.Kind kind,String link,String address,String agent,Instant at) {
        if(!enabled()||records.findById(resource).filter(r->r.getStatus()==PublicationStatus.PUBLISHED).isEmpty())return;
        if(postgres)entities.createNativeQuery("select set_config('lock_timeout', '2000ms', true)").getSingleResult();
        String key=key(resource,kind,link,address,agent,at);
        var old=observations.lock(key);
        if(old.isPresent())old.get().observe(at);
        else observations.saveAndFlush(new UsageObservation(key,resource,kind,at));
    }
    String key(String resource,UsageObservation.Kind kind,String link,String address,String agent,Instant at) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            // Length-delimited values avoid ambiguous concatenation. UTC day prevents long-term linking.
            for(String value:List.of(at.atZone(ZoneOffset.UTC).toLocalDate().toString(),resource,kind.name(),link,address,agent)) {
                byte[] bytes=value.getBytes(StandardCharsets.UTF_8);mac.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());mac.update(bytes);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        }catch(java.security.GeneralSecurityException failure){throw new IllegalStateException("Usage pseudonymization unavailable.",failure);}
    }
    public record Row(String resourceId,LocalDate day,UsageObservation.Kind kind,long requests){}
    @io.swagger.v3.oas.annotations.media.Schema(name="LocalUsageReport")
 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
 public record Report(String schema,Instant generatedAt,LocalDate from,LocalDate until,boolean collectionEnabled,
                         long views,long downloads,List<Row> rows,String methodology,boolean counterCertified){}
    @Transactional(readOnly=true)
    public Report report(LocalDate from,LocalDate until,String resource,boolean includeRows) {
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        LocalDate end=until==null?today:until,start=from==null?end.minusDays(29):from;
        if(start.isAfter(end)||end.isAfter(today)||start.isBefore(today.minusDays(89)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Seleccione un período UTC dentro de los últimos 90 días.");
        if(resource!=null&&records.findById(resource).filter(r->r.getStatus()==PublicationStatus.PUBLISHED).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var rows=observations.aggregate(start,end,resource).stream().map(r->new Row(r.getResourceId(),r.getUsageDay(),r.getKind(),r.getRequests())).toList();
        long views=rows.stream().filter(r->r.kind()==UsageObservation.Kind.VIEW).mapToLong(Row::requests).sum();
        long downloads=rows.stream().filter(r->r.kind()==UsageObservation.Kind.DOWNLOAD).mapToLong(Row::requests).sum();
        return new Report("reduniv.local-usage.v1",Instant.now(),start,end,enabled(),views,downloads,includeRows?rows:List.of(),
            "PUBLIC_GET_SUCCESS; EXCLUDE_INLINE_HEAD_PREFETCH_KNOWN_BOTS; SLIDING_30_SECONDS_PER_LINK_DAILY_HMAC_IP_UA; UTC_90_DAY_RETENTION; CURRENTLY_PUBLISHED_ONLY; NOT_UNIQUE_PEOPLE_OR_CONFIRMED_RECEIPTS",false);
    }
    @Scheduled(cron="0 23 4 * * *",zone="UTC") @Transactional
    public void purge(){observations.deleteByUsageDayBefore(LocalDate.now(ZoneOffset.UTC).minusDays(89));}
}
