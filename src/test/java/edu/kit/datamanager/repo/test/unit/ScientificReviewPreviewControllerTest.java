package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.web.impl.ScientificReviewPreviewController;
import java.util.Optional;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScientificReviewPreviewControllerTest {
    @Test public void previewRequiresInReviewStatus() {
        ScientificRecordRepository records = mock(ScientificRecordRepository.class);
        IDataResourceDao resources = mock(IDataResourceDao.class);
        IContentInformationDao contents = mock(IContentInformationDao.class);
        ScientificReviewPreviewController controller = new ScientificReviewPreviewController(records, resources, contents);
        ScientificRecord record = new ScientificRecord("r1");
        when(records.findById("r1")).thenReturn(Optional.of(record));
        assertThrows(ResponseStatusException.class, () -> controller.detail("r1"));
        record.setStatus(PublicationStatus.IN_REVIEW);
        DataResource resource = new DataResource();
        when(resources.findById("r1")).thenReturn(Optional.of(resource));
        var preview = controller.detail("r1");
        assertEquals("r1", preview.id());
        assertEquals("No hay description.md.", preview.markdown());
    }
}
