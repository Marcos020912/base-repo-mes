package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.service.RepositoryStorageStatusService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/scientific/preservation/storage")
@PreAuthorize("hasAnyAuthority('ROLE_CURATOR','ROLE_ADMINISTRATOR')")
public class OperationalStorageController {
    private final RepositoryStorageStatusService storage;
    public OperationalStorageController(RepositoryStorageStatusService storage){this.storage=storage;}
    @GetMapping public ResponseEntity<RepositoryStorageStatusService.StorageStatus> status(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(storage.status());}
}
