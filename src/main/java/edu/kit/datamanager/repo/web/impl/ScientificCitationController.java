package edu.kit.datamanager.repo.web.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.Agent;
import edu.kit.datamanager.repo.domain.PublicationStatus;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Version-specific citation exports for already published resources. */
@RestController
@RequestMapping("/api/v1/scientific/{id}/citation")
public class ScientificCitationController {
    private final IDataResourceDao resources;
    private final ScientificRecordRepository records;
    private final ObjectMapper mapper;

    public ScientificCitationController(IDataResourceDao resources, ScientificRecordRepository records, ObjectMapper mapper) {
        this.resources = resources;
        this.records = records;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<String> export(@PathVariable String id, @RequestParam(defaultValue = "apa") String format) throws JsonProcessingException {
        ScientificRecord science = records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ficha científica no encontrada."));
        if (science.getStatus() != PublicationStatus.PUBLISHED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo una versión publicada tiene una cita definitiva.");
        DataResource resource = resources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado."));
        String title = resource.getTitles().stream().findFirst().map(item -> item.getValue()).orElse("Sin título");
        List<String> authors = resource.getCreators().stream().map(item -> String.join(" ",
                item.getGivenName() == null ? "" : item.getGivenName(),
                item.getFamilyName() == null ? "" : item.getFamilyName()).trim()).filter(name -> !name.isBlank()).toList();
        String year = safe(resource.getPublicationYear());
        String doi = science.getVersionDoi();
        String version = science.getVersionLabel();
        String publisher = safe(resource.getPublisher());
        List<Agent> creatorAgents = resource.getCreators().stream().toList();
        String filename = "citation-" + id.replaceAll("[^A-Za-z0-9_-]", "_");
        String result; MediaType mime; String extension;
        switch (format.toLowerCase(java.util.Locale.ROOT)) {
            case "apa" -> { result = String.join(", ", authors) + " (" + year + "). " + title + " (versión " + version + ") [Conjunto de datos]. " + publisher + ". https://doi.org/" + doi; mime = MediaType.TEXT_PLAIN; extension = "txt"; }
            case "vancouver" -> { result = String.join(", ", creatorAgents.stream().map(ScientificCitationController::vancouverName).toList())
                    + ". " + safe(title) + " [conjunto de datos]. Versión " + safe(version) + ". " + publisher + "; " + year + ". doi:" + safe(doi); mime = MediaType.TEXT_PLAIN; extension = "txt"; }
            case "chicago" -> { result = String.join(", ", authors.stream().map(ScientificCitationController::safe).toList())
                    + ". \"" + safe(title) + ".\" Conjunto de datos, versión " + safe(version) + ". "
                    + publisher + ", " + year + ". https://doi.org/" + safe(doi); mime = MediaType.TEXT_PLAIN; extension = "txt"; }
            case "ieee" -> { result = String.join(", ", creatorAgents.stream().map(ScientificCitationController::ieeeName).toList())
                    + ", \"" + safe(title) + ",\" conjunto de datos, " + publisher + ", ver. "
                    + safe(version) + ", " + year + ", doi: " + safe(doi) + "."; mime = MediaType.TEXT_PLAIN; extension = "txt"; }
            case "bibtex" -> { result = "@dataset{" + filename + ",\n  author = {" + creatorAgents.stream().map(ScientificCitationController::bibAuthor).collect(java.util.stream.Collectors.joining(" and ")) + "},\n  title = {" + bib(title) + "},\n  year = {" + bib(year) + "},\n  publisher = {" + bib(publisher) + "},\n  version = {" + bib(version) + "},\n  doi = {" + bib(doi) + "}\n}\n"; mime = MediaType.TEXT_PLAIN; extension = "bib"; }
            case "ris" -> { result = "TY  - DATA\n" + creatorAgents.stream().map(author -> "AU  - " + risAuthor(author) + "\n").reduce("", String::concat) + "TI  - " + safe(title) + "\nPY  - " + year + "\nPB  - " + publisher + "\nDO  - " + doi + "\nET  - " + safe(version) + "\nER  - \n"; mime = MediaType.TEXT_PLAIN; extension = "ris"; }
            case "csl-json" -> {
                Map<String, Object> csl = new LinkedHashMap<>();
                csl.put("id", doi); csl.put("type", "dataset"); csl.put("title", title);
                csl.put("author", creatorAgents.stream().map(ScientificCitationController::cslAuthor).toList());
                if (year.matches("\\d{4}")) csl.put("issued", Map.of("date-parts", List.of(List.of(Integer.parseInt(year)))));
                csl.put("publisher", publisher); csl.put("DOI", doi); csl.put("version", version);
                result = mapper.writeValueAsString(csl); mime = MediaType.APPLICATION_JSON; extension = "json";
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de cita no admitido.");
        }
        return ResponseEntity.ok().contentType(mime).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "." + extension + "\"").body(result);
    }

    private static String safe(String input) { return input == null ? "" : input.replaceAll("[\\r\\n\\t]+", " ").trim(); }
    private static String bib(String input) {
        StringBuilder escaped = new StringBuilder();
        for (int code : safe(input).codePoints().toArray()) {
            switch (code) {
                case '\\' -> escaped.append("\\textbackslash{}");
                case '{', '}', '%', '&', '_', '#', '$' -> escaped.append('\\').appendCodePoint(code);
                case '~' -> escaped.append("\\textasciitilde{}");
                case '^' -> escaped.append("\\textasciicircum{}");
                default -> escaped.appendCodePoint(code);
            }
        }
        return escaped.toString();
    }
    private static String bibAuthor(Agent author) {
        String family = safe(author.getFamilyName()), given = safe(author.getGivenName());
        // A literal organization/name without family must not be split at "and".
        if (family.isBlank()) return "{" + bib(given) + "}";
        return bib(family) + (given.isBlank() ? "" : ", " + bib(given));
    }
    private static String risAuthor(Agent author) {
        String family = safe(author.getFamilyName()), given = safe(author.getGivenName());
        return family.isBlank() ? given : family + (given.isBlank() ? "" : ", " + given);
    }
    private static Map<String, String> cslAuthor(Agent author) {
        String family = safe(author.getFamilyName()), given = safe(author.getGivenName());
        return family.isBlank() ? Map.of("literal", given) : Map.of("given", given, "family", family);
    }
    private static String initials(String given) {
        if (given == null || given.isBlank()) return "";
        return java.util.Arrays.stream(given.trim().split("\\s+"))
                .filter(part -> !part.isBlank()).map(part -> part.substring(0, 1).toUpperCase()).reduce("", String::concat);
    }
    private static String vancouverName(Agent author) {
        return (safe(author.getFamilyName()) + " " + initials(author.getGivenName())).trim();
    }
    private static String ieeeName(Agent author) {
        String first = initials(author.getGivenName());
        String dotted = first.replaceAll("(.)", "$1.");
        return (dotted + " " + safe(author.getFamilyName())).trim();
    }
}
