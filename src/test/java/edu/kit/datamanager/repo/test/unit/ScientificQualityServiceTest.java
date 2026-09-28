package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.service.ScientificQualityService;
import java.util.Optional;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScientificQualityServiceTest {
    @Test public void missingDocumentationAndDataBlockReview() {
        IDataResourceDao resources = mock(IDataResourceDao.class);
        IContentInformationDao contents = mock(IContentInformationDao.class);
        DataResource resource = new DataResource();
        when(resources.findById("r1")).thenReturn(Optional.of(resource));
        when(contents.findByParentResourceAndRelativePath(resource, "description.md")).thenReturn(Optional.empty());
        when(contents.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        ScientificRecord science = new ScientificRecord("r1");
        science.setVersionLabel("1.0"); science.setLicenseId("CC-BY-4.0");
        science.setInstitution("Universidad"); science.setMethodology("Medición");
        var report = new ScientificQualityService(resources, contents).inspect(science);
        assertTrue(report.blockers().contains("description.md"));
        assertTrue(report.blockers().contains("Al menos un archivo de datos"));
        assertTrue(report.completionPercent() < 100);
    }
}
