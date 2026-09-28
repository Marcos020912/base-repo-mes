package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.service.ScientificVocabularyService;
import org.junit.Test;

import static org.junit.Assert.*;

public class ScientificVocabularyServiceTest {
    @Test public void strictModeRejectsValuesOutsideConfiguredLists() {
        var service = new ScientificVocabularyService("CC-BY-4.0,CC0-1.0", "Física,Biología", true);
        assertTrue(service.validLicense("CC-BY-4.0"));
        assertFalse(service.validLicense("private"));
        assertTrue(service.validDiscipline("Física"));
        assertFalse(service.validDiscipline("Filosofía"));
    }
    @Test public void permissiveModeKeepsLegacyDepositsEditable() {
        var service = new ScientificVocabularyService("CC-BY-4.0", "Física", false);
        assertTrue(service.validLicense("Licencia heredada"));
        assertTrue(service.validDiscipline("Especialidad local"));
    }
}
