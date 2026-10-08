package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.FileProvenanceEvent;
import edu.kit.datamanager.repo.service.W3cProvExporter;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;

public class W3cProvExporterTest {
    @SuppressWarnings("unchecked")
    @Test public void linksRecordedStorageAndDeletionWithoutInventingEvents() {
        var stored = new FileProvenanceEvent("r1", 7L, "image.png", "STORED", "alice", "a".repeat(64));
        var deleted = new FileProvenanceEvent("r1", 7L, "image.png", "DELETED", "alice", "a".repeat(64));
        var other = new FileProvenanceEvent("r2", 8L, "secret.png", "STORED", "bob", null);
        var document = W3cProvExporter.build("r1", List.of(stored, deleted, other));
        var context = (Map<String, String>) document.get("@context");
        assertEquals("http://www.w3.org/ns/prov#", context.get("prov"));
        var graph = (List<Map<String, Object>>) document.get("@graph");
        var file = graph.stream().filter(node -> "#file-index-0".equals(node.get("@id"))).findFirst().orElseThrow();
        assertEquals(Map.of("@id", "#activity-index-0"), file.get("prov:wasGeneratedBy"));
        assertEquals(Map.of("@id", "#activity-index-1"), file.get("prov:wasInvalidatedBy"));
        assertEquals("a".repeat(64), file.get("schema:sha256"));
        assertEquals("xsd:dateTime", ((Map<?, ?>) file.get("prov:generatedAtTime")).get("@type"));
        assertEquals("xsd:dateTime", ((Map<?, ?>) file.get("prov:invalidatedAtTime")).get("@type"));
        var deletion = graph.stream().filter(node -> "#activity-index-1".equals(node.get("@id"))).findFirst().orElseThrow();
        assertEquals(Map.of("@id", "#file-index-0"), deletion.get("prov:used"));
        assertTrue(graph.stream().noneMatch(node -> node.toString().contains("secret.png")));
        assertEquals(1, graph.stream().filter(node -> "prov:Agent".equals(node.get("@type"))).count());
    }

    @SuppressWarnings("unchecked")
    @Test public void deletionWithoutKnownStorageHasNoInventedGeneration() {
        var deleted = new FileProvenanceEvent("r1", 7L, "legacy.txt", "DELETED", "SYSTEM", null);
        var graph = (List<Map<String, Object>>) W3cProvExporter.build("r1", List.of(deleted)).get("@graph");
        var file = graph.stream().filter(node -> "#historical-file-index-0".equals(node.get("@id")))
                .findFirst().orElseThrow();
        assertFalse(file.containsKey("prov:wasGeneratedBy"));
        assertEquals(Map.of("@id", "#activity-index-0"), file.get("prov:wasInvalidatedBy"));
        assertTrue(graph.stream().anyMatch(node -> "prov:SoftwareAgent".equals(node.get("@type"))));
    }

    @SuppressWarnings("unchecked")
    @Test public void scientificNarrativeDoesNotFabricateExecutionActivities(){
        var record=new edu.kit.datamanager.repo.domain.ScientificRecord("r1");record.setProductionDescription("Instrument readings");record.setProcessingDescription("Cleaning missing values");record.setProcessingTools("Tool 2.0");
        var graph=(List<Map<String,Object>>)W3cProvExporter.build("r1",List.of(),record).get("@graph");assertEquals(1,graph.size());
        var declaration=graph.get(0);assertEquals("prov:Entity",declaration.get("@type"));assertTrue(declaration.get("schema:description").toString().contains("Cleaning missing values"));assertFalse(declaration.containsKey("prov:generatedAtTime"));assertFalse(declaration.containsKey("prov:wasGeneratedBy"));
        record.setProductionDescription(null);record.setProcessingDescription(null);record.setProcessingTools(null);assertTrue(((List<?>)W3cProvExporter.build("r1",List.of(),record).get("@graph")).isEmpty());
    }
}
