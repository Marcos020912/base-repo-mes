package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.service.ScientificQualityService;
import edu.kit.datamanager.repo.repository.ScientificCreatorRepository;
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
        ScientificCreatorRepository creators = mock(ScientificCreatorRepository.class);
        when(creators.findByResourceId("r1")).thenReturn(java.util.List.of());
        DataResource resource = new DataResource();
        when(resources.findById("r1")).thenReturn(Optional.of(resource));
        when(contents.findByParentResourceAndRelativePath(resource, "description.md")).thenReturn(Optional.empty());
        when(contents.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        ScientificRecord science = new ScientificRecord("r1");
        science.setVersionLabel("1.0"); science.setLicenseId("CC-BY-4.0");
        science.setInstitution("Universidad"); science.setMethodology("Medición");
        var report = new ScientificQualityService(resources, contents, creators).inspect(science);
        assertTrue(report.blockers().contains("description.md"));
        assertTrue(report.blockers().contains("Al menos un archivo de datos"));
        assertTrue(report.completionPercent() < 100);
    }
    @Test public void frozenProfileAddsBlockersWithoutRemovingBaseRequirements(){
        var resources=mock(IDataResourceDao.class);var contents=mock(IContentInformationDao.class);var creators=mock(ScientificCreatorRepository.class);var resource=new DataResource();when(resources.findById("profiled")).thenReturn(Optional.of(resource));when(creators.findByResourceId("profiled")).thenReturn(java.util.List.of());when(contents.findByParentResourceAndRelativePath(resource,"description.md")).thenReturn(Optional.empty());when(contents.findAll(any(Specification.class),any(Pageable.class))).thenReturn(Page.empty());
        var science=new ScientificRecord("profiled");science.setMetadataProfileRequiredFields(new java.util.LinkedHashSet<>(java.util.Set.of("processingTools","summary")));
        var quality=new ScientificQualityService(resources,contents,creators);var report=quality.inspect(science);
        assertTrue(report.blockers().contains("Licencia"));assertTrue(report.blockers().contains("Perfil: Herramientas y versiones"));assertTrue(report.blockers().contains("Perfil: Resumen científico"));
        science.setSummary("Resumen declarado");science.setProcessingTools("R 4.4");var updated=quality.inspect(science);assertFalse(updated.blockers().contains("Perfil: Herramientas y versiones"));assertTrue(updated.blockers().contains("Licencia"));
    }
}
