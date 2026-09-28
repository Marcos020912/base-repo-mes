package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.DoiRegistration;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.DoiRegistrationRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.web.impl.PublicDatasetLandingController;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PublicDatasetLandingControllerTest {
    @Test
    public void versionHasStableLandingAndConceptPointsToLatestPublishedVersion() {
        DoiRegistrationRepository registrations = mock(DoiRegistrationRepository.class);
        ScientificRecordRepository records = mock(ScientificRecordRepository.class);
        var controller = new PublicDatasetLandingController(registrations, records);
        when(registrations.findById("c:root")).thenReturn(Optional.of(new DoiRegistration("c:root", "root", "CONCEPT", "10.1234/c-root", "api.test.datacite.org")));
        ScientificRecord latest = new ScientificRecord("v2");
        when(records.findByConceptualDoiIgnoreCaseAndStatusOrderByPublishedAtDesc("10.1234/c-root", PublicationStatus.PUBLISHED))
                .thenReturn(List.of(latest));
        assertEquals("forward:/public-resource.html", controller.landing("v2"));
        assertEquals("redirect:/datasets/v2", controller.concept("root"));
    }
}
