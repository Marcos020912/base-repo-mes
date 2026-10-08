package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.Description;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.domain.Title;
import edu.kit.datamanager.repo.service.RoCrateMetadataBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;

public class RoCrateMetadataBuilderTest {
    @Test public void publishedCrateReferencesExactVersionDoiAndFileData() {
        DataResource resource = DataResource.factoryNewDataResource("crate-test");
        resource.getTitles().add(Title.factoryTitle("Datos costeros", Title.TYPE.OTHER));
        resource.getDescriptions().add(Description.factoryDescription("Mediciones del litoral", Description.TYPE.OTHER, "es"));
        resource.getCreators().add(Agent.factoryAgent("Ana", "Pérez", new String[]{}));
        ScientificRecord record = new ScientificRecord(resource.getId());
        record.setStatus(PublicationStatus.PUBLISHED);
        record.setVersionDoi("10.1234/costa.v2");
        record.setVersionLabel("2.0");record.setProductionDescription("Sensores");record.setProcessingDescription("Limpieza");record.setProcessingTools("Software 1.0");
        record.setLicenseId("CC-BY-4.0");
        record.setPublishedAt(Instant.parse("2026-09-01T00:00:00Z"));
        var crate = RoCrateMetadataBuilder.build(resource.getId(), record, resource,
                List.of(new RoCrateMetadataBuilder.PackagedFile("data/a #1.csv", 42, "text/csv", "f".repeat(64))),
                Instant.parse("2026-10-08T12:00:00Z"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> graph = (List<Map<String, Object>>) crate.get("@graph");
        Map<String, Object> root = graph.get(1);
        assertEquals("Datos costeros", root.get("name"));
        assertEquals(Map.of("@id","#scientific-provenance"),root.get("subjectOf"));assertTrue(graph.stream().anyMatch(node->"#scientific-provenance".equals(node.get("@id"))&&node.get("description").toString().contains("Software 1.0")));
        assertEquals("Mediciones del litoral", root.get("description"));
        assertEquals(Map.of("@id", "#license"), root.get("license"));
        assertTrue(graph.stream().anyMatch(node -> "#license".equals(node.get("@id"))
                && "CC-BY-4.0".equals(node.get("name"))));
        assertEquals("https://doi.org/10.1234/costa.v2", root.get("sameAs"));
        assertEquals("2.0", root.get("version"));
        assertEquals("2026-09-01T00:00:00Z", root.get("datePublished"));
        assertTrue(graph.stream().anyMatch(node -> "Person".equals(node.get("@type")) && "Ana Pérez".equals(node.get("name"))));
        assertTrue(graph.stream().anyMatch(node -> "data/a%20%231.csv".equals(node.get("@id"))
                && "f".repeat(64).equals(node.get("sha256")) && "text/csv".equals(node.get("encodingFormat"))));
    }
}
