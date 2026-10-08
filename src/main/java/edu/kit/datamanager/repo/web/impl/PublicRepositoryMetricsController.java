package edu.kit.datamanager.repo.web.impl;

import edu.kit.datamanager.repo.service.PublicRepositoryMetricsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/metrics")
public class PublicRepositoryMetricsController {
    private final PublicRepositoryMetricsService metrics;
    public PublicRepositoryMetricsController(PublicRepositoryMetricsService metrics) {this.metrics=metrics;}
    @GetMapping public ResponseEntity<PublicRepositoryMetricsService.Metrics> snapshot() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(metrics.snapshot());
    }
}
