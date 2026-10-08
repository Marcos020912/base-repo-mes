package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.domain.FileProvenanceEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PROV-O projection of the web file events that actually exist; it does not infer missing history. */
public final class W3cProvExporter {
    private W3cProvExporter() {}

    public static Map<String, Object> build(String resourceId, List<FileProvenanceEvent> events) {
        List<Map<String, Object>> graph = new ArrayList<>();
        Map<String, Map<String, Object>> latestStored = new HashMap<>();
        Set<String> seenAgents = new HashSet<>();
        Set<String> seenLocations = new HashSet<>();
        for (int index = 0; index < events.size(); index++) {
            FileProvenanceEvent event = events.get(index);
            if (!resourceId.equals(event.getResourceId())) continue;
            String suffix = event.getId() == null ? "index-" + index : "event-" + event.getId();
            String activityId = "#activity-" + suffix;
            String actor = event.getActor() == null || event.getActor().isBlank() ? "SYSTEM" : event.getActor();
            String agentId = "#agent-" + sha256(actor);
            String path = event.getRelativePath();
            if (path == null || path.isBlank()) continue;
            String locationId = "#location-" + sha256(path);
            if (seenAgents.add(agentId)) graph.add(Map.of("@id", agentId,
                    "@type", "SYSTEM".equals(actor) ? "prov:SoftwareAgent" : "prov:Agent",
                    "rdfs:label", actor));
            if (seenLocations.add(locationId)) graph.add(Map.of("@id", locationId,
                    "@type", "prov:Location", "rdfs:label", path));
            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put("@id", activityId); activity.put("@type", "prov:Activity");
            activity.put("rdfs:label", "STORED".equals(event.getAction()) ? "Carga de archivo"
                    : "DELETED".equals(event.getAction()) ? "Eliminación de archivo" : "Evento de archivo");
            activity.put("prov:wasAssociatedWith", ref(agentId));
            if (event.getOccurredAt() != null) activity.put("prov:endedAtTime", dateTime(event.getOccurredAt().toString()));
            graph.add(activity);
            String key = event.getContentId() + "|" + path;
            if ("STORED".equals(event.getAction())) {
                String entityId = "#file-" + suffix;
                Map<String, Object> entity = new LinkedHashMap<>();
                entity.put("@id", entityId); entity.put("@type", "prov:Entity");
                entity.put("rdfs:label", path);
                entity.put("prov:atLocation", ref(locationId));
                entity.put("prov:wasGeneratedBy", ref(activityId));
                if (event.getOccurredAt() != null) entity.put("prov:generatedAtTime", dateTime(event.getOccurredAt().toString()));
                if (event.getSha256() != null && event.getSha256().matches("(?i)[0-9a-f]{64}"))
                    entity.put("schema:sha256", event.getSha256());
                graph.add(entity); latestStored.put(key, entity);
            } else if ("DELETED".equals(event.getAction())) {
                Map<String, Object> entity = latestStored.remove(key);
                if (entity == null) {
                    // Earlier storage may have happened outside the web endpoints we audit.
                    entity = new LinkedHashMap<>();
                    entity.put("@id", "#historical-file-" + suffix); entity.put("@type", "prov:Entity");
                    entity.put("rdfs:label", path); entity.put("prov:atLocation", ref(locationId));
                    if (event.getSha256() != null && event.getSha256().matches("(?i)[0-9a-f]{64}"))
                        entity.put("schema:sha256", event.getSha256());
                    graph.add(entity);
                }
                entity.put("prov:wasInvalidatedBy", ref(activityId));
                if (event.getOccurredAt() != null) entity.put("prov:invalidatedAtTime", dateTime(event.getOccurredAt().toString()));
                activity.put("prov:used", ref((String) entity.get("@id")));
            }
        }
        return Map.of("@context", Map.of(
                "prov", "http://www.w3.org/ns/prov#",
                "rdfs", "http://www.w3.org/2000/01/rdf-schema#",
                "schema", "https://schema.org/",
                "xsd", "http://www.w3.org/2001/XMLSchema#"), "@graph", graph);
    }

    private static Map<String, String> ref(String id) { return Map.of("@id", id); }
    private static Map<String, String> dateTime(String value) {
        return Map.of("@value", value, "@type", "xsd:dateTime");
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
