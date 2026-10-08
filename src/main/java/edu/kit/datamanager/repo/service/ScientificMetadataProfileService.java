package edu.kit.datamanager.repo.service;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ScientificMetadataProfileService {
    public static final Map<String,String> FIELDS=Map.ofEntries(Map.entry("summary","Resumen científico"),Map.entry("language","Idioma"),Map.entry("discipline","Disciplina"),Map.entry("keywords","Palabras clave"),Map.entry("productionDescription","Producción y origen"),Map.entry("processingDescription","Procesamiento científico"),Map.entry("processingTools","Herramientas y versiones"),Map.entry("temporalStart","Inicio de cobertura temporal"),Map.entry("temporalEnd","Fin de cobertura temporal"),Map.entry("geographicCoverage","Cobertura geográfica"),Map.entry("translations","Traducciones de metadatos"));
    private final ScientificMetadataProfileRepository profiles;
    private final ScientificRecordRepository records;
    private final ScientificVocabularyService vocabulary;
    public ScientificMetadataProfileService(ScientificMetadataProfileRepository profiles,ScientificRecordRepository records,ScientificVocabularyService vocabulary){this.profiles=profiles;this.records=records;this.vocabulary=vocabulary;}
    @Transactional(readOnly=true) public List<ApprovedView> available(){return profiles.findByActiveTrueOrderByIdAsc().stream().filter(profile->profile.getApproved()!=null).map(profile->new ApprovedView(profile.getId(),profile.getApprovedRevision(),profile.getApproved().copy(),Set.copyOf(profile.getApprovedRequiredFields()))).toList();}
    @Transactional(readOnly=true) public List<AdminView> administration(){return profiles.findAll().stream().sorted(Comparator.comparing(ScientificMetadataProfile::getId)).map(ScientificMetadataProfileService::view).toList();}
    @Transactional public AdminView propose(String id,ScientificMetadataProfileDefinition definition,Set<String> requiredFields,String note,Long revision,String actor){
        if(id==null||!id.matches("[a-z][a-z0-9-]{0,49}"))throw bad("Identificador de perfil no válido; use letras minúsculas, números y guiones.");
        var current=profiles.findById(id).orElse(null);check(current,revision);
        var cleaned=normalize(definition);if(requiredFields!=null&&requiredFields.stream().anyMatch(Objects::isNull))throw bad("No se admiten campos nulos.");var fields=requiredFields==null?Set.<String>of():Set.copyOf(requiredFields);
        if(!FIELDS.keySet().containsAll(fields))throw bad("El perfil contiene campos no admitidos; no puede relajar requisitos base.");
        String reason=prose(note,1000);if(reason==null)throw bad("Explique el motivo de la propuesta.");
        if(current==null){if(profiles.count()>=100)throw conflict("Se alcanzó el límite de cien perfiles; reutilice o revise los existentes.");current=new ScientificMetadataProfile(id);}
        current.setProposed(cleaned);current.setProposedRequiredFields(new LinkedHashSet<>(fields));current.setProposalNote(reason);current.setProposedBy(actor);current.setProposedAt(Instant.now());current.setUpdatedBy(actor);current.setUpdatedAt(Instant.now());return view(profiles.saveAndFlush(current));
    }
    @Transactional public AdminView approve(String id,Long revision,String actor){
        var profile=profiles.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Perfil no encontrado."));check(profile,revision);
        if(profile.getProposed()==null)throw conflict("No hay propuesta pendiente.");validateDefaults(profile.getProposed());
        profile.setApproved(profile.getProposed().copy());profile.setApprovedRequiredFields(new LinkedHashSet<>(profile.getProposedRequiredFields()));profile.setApprovedRevision(profile.getRevision()+1);profile.setProposed(null);profile.setProposedRequiredFields(new LinkedHashSet<>());profile.setActive(true);profile.setApprovedBy(actor);profile.setApprovedAt(Instant.now());profile.setUpdatedBy(actor);profile.setUpdatedAt(Instant.now());return view(profiles.saveAndFlush(profile));
    }
    @Transactional public AdminView setActive(String id,Long revision,boolean active,String actor){
        var profile=profiles.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Perfil no encontrado."));check(profile,revision);
        if(active&&profile.getApproved()==null)throw conflict("Debe aprobar un perfil antes de activarlo.");profile.setActive(active);profile.setUpdatedBy(actor);profile.setUpdatedAt(Instant.now());return view(profiles.saveAndFlush(profile));
    }
    /** Called only after owner authorization. Never changes an existing nonblank author declaration. */
    @Transactional public ScientificRecord apply(String resourceId,String profileId,Long profileRevision,Long resourceRevision){
        var record=records.findById(resourceId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Guarde primero la ficha científica."));
        if(record.getStatus()!=PublicationStatus.DRAFT)throw conflict("Solo se puede cambiar el perfil de un borrador.");
        if(resourceRevision==null||resourceRevision!=record.getRevision())throw conflict("El borrador cambió; recargue antes de aplicar el perfil.");
        if(profileId==null||profileId.isBlank()){record.setMetadataProfileId(null);record.setMetadataProfileName(null);record.setMetadataProfileRevision(null);record.setMetadataProfileRequiredFields(new LinkedHashSet<>());}
        else{
            var profile=profiles.findById(profileId).filter(ScientificMetadataProfile::isActive).filter(value->value.getApproved()!=null).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Perfil aprobado no disponible."));
            if(profileRevision==null||!profileRevision.equals(profile.getApprovedRevision()))throw conflict("El perfil cambió; revise sus reglas antes de aplicarlo.");
            validateDefaults(profile.getApproved());
            record.setMetadataProfileId(profile.getId());record.setMetadataProfileName(profile.getApproved().getName());record.setMetadataProfileRevision(profile.getApprovedRevision());record.setMetadataProfileRequiredFields(new LinkedHashSet<>(profile.getApprovedRequiredFields()));
            if(!text(record.getLicenseId()))record.setLicenseId(profile.getApproved().getLicenseId());if(!text(record.getLanguage()))record.setLanguage(profile.getApproved().getLanguage());if(!text(record.getDiscipline()))record.setDiscipline(profile.getApproved().getDiscipline());
        }
        return records.saveAndFlush(record);
    }
    public static boolean complete(ScientificRecord record,String field){return switch(field){
        case "summary"->text(record.getSummary());case "language"->text(record.getLanguage());case "discipline"->text(record.getDiscipline());case "keywords"->text(record.getKeywords());case "productionDescription"->text(record.getProductionDescription());case "processingDescription"->text(record.getProcessingDescription());case "processingTools"->text(record.getProcessingTools());case "temporalStart"->record.getTemporalStart()!=null;case "temporalEnd"->record.getTemporalEnd()!=null;case "geographicCoverage"->text(record.getGeographicCoverage());case "translations"->record.getTranslations()!=null&&!record.getTranslations().isEmpty();default->false;};}
    private void validateDefaults(ScientificMetadataProfileDefinition definition){if(!vocabulary.validLicense(definition.getLicenseId())||!vocabulary.validDiscipline(definition.getDiscipline()))throw bad("Los valores iniciales no pertenecen al vocabulario activo.");}
    private static ScientificMetadataProfileDefinition normalize(ScientificMetadataProfileDefinition input){
        if(input==null)throw bad("Indique la definición del perfil.");String name=clean(input.getName(),100);if(name==null)throw bad("Indique el nombre del perfil.");String language=clean(input.getLanguage(),16);
        if(language!=null){if(!language.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*"))throw bad("Idioma BCP47 no válido.");try{language=new Locale.Builder().setLanguageTag(language).build().toLanguageTag();}catch(IllformedLocaleException error){throw bad("Idioma BCP47 no válido.");}if(language.equals("und"))throw bad("Idioma BCP47 no válido.");}
        return new ScientificMetadataProfileDefinition(name,prose(input.getDescription(),1000),clean(input.getLicenseId(),100),language,clean(input.getDiscipline(),255));
    }
    private static String prose(String value,int max){if(value==null||value.isBlank())return null;String result=value.strip();if(result.length()>max||result.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw bad("El texto excede su límite o contiene caracteres de control.");return result;}
    private static String clean(String value,int max){if(value==null||value.isBlank())return null;String result=value.strip();if(result.length()>max||result.codePoints().anyMatch(Character::isISOControl))throw bad("El texto excede su límite o contiene caracteres de control.");return result;}
    private static void check(ScientificMetadataProfile profile,Long revision){if(profile==null?revision!=null:revision==null||revision!=profile.getRevision())throw conflict("El perfil cambió; vuelva a consultarlo antes de guardar.");}
    private static boolean text(String value){return value!=null&&!value.isBlank();}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static AdminView view(ScientificMetadataProfile profile){return new AdminView(profile.getId(),profile.getRevision(),profile.getApprovedRevision(),profile.isActive(),profile.getApproved(),profile.getProposed(),Set.copyOf(profile.getApprovedRequiredFields()),Set.copyOf(profile.getProposedRequiredFields()),profile.getProposalNote(),profile.getProposedBy(),profile.getProposedAt(),profile.getApprovedBy(),profile.getApprovedAt(),profile.getUpdatedBy(),profile.getUpdatedAt());}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    @io.swagger.v3.oas.annotations.media.Schema(name="ApprovedMetadataProfile") public record ApprovedView(String id,Long revision,ScientificMetadataProfileDefinition definition,Set<String> requiredFields){}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS) @io.swagger.v3.oas.annotations.media.Schema(name="AdministrativeMetadataProfile")
    public record AdminView(String id,long revision,Long approvedRevision,boolean active,ScientificMetadataProfileDefinition approved,ScientificMetadataProfileDefinition proposed,Set<String> approvedRequiredFields,Set<String> proposedRequiredFields,String proposalNote,String proposedBy,Instant proposedAt,String approvedBy,Instant approvedAt,String updatedBy,Instant updatedAt){}
}
