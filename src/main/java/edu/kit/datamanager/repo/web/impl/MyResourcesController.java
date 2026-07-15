package edu.kit.datamanager.repo.web.impl;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.util.AuthenticationHelper;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/my-dataresources")
public class MyResourcesController {
    private final ResourceOwnershipRepository ownership; private final IDataResourceDao resources;
    public MyResourcesController(ResourceOwnershipRepository ownership, IDataResourceDao resources) { this.ownership = ownership; this.resources = resources; }
    @GetMapping public List<DataResource> mine() { return ownership.findByUsernameIgnoreCaseOrderByCreatedAtDesc(AuthenticationHelper.getPrincipal()).stream().map(item -> resources.findById(item.getResourceId()).orElse(null)).filter(java.util.Objects::nonNull).toList(); }
}
