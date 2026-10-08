package edu.kit.datamanager.repo.service;

import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.*;
import edu.kit.datamanager.repo.repository.*;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class ScientificPrivacyService {
    private final ScientificPrivacyAssessmentRepository assessments;
    private final ScientificRecordRepository records;
    private final IDataResourceDao resources;
    private final ResourceOwnershipRepository owners;
    private final ScientificRecordEventRepository events;
    private final boolean required;
    public ScientificPrivacyService(ScientificPrivacyAssessmentRepository assessments,ScientificRecordRepository records,
            IDataResourceDao resources,ResourceOwnershipRepository owners,ScientificRecordEventRepository events,
            @Value("${repo.privacy.require-assessment:false}") boolean required) {
        this.assessments=assessments;this.records=records;this.resources=resources;this.owners=owners;this.events=events;this.required=required;
    }
    public boolean isRequired() {return required;}
    public record Assessment(String resourceId,Long revision,ScientificPrivacyAssessment.Classification classification,
            String assessmentNote,String reviewState,Instant updatedAt,Instant reviewedAt,String reviewedBy,String reviewNote) {}
    private ScientificRecord resource(String id) {
        if(!resources.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Depósito no encontrado.");
        return records.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Ficha no encontrada."));
    }
    private String username() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return auth.getName();
    }
    private boolean owner(String id) {return owners.findById(id).filter(item->item.getUsername().equalsIgnoreCase(username())).isPresent();}
    private boolean curator() {
        username();return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .anyMatch(item->"ROLE_CURATOR".equals(item.getAuthority()) || "ROLE_ADMINISTRATOR".equals(item.getAuthority()));
    }
    private Assessment view(String id) {
        return assessments.findById(id).map(a->new Assessment(id,a.getRevision(),a.getClassification(),a.getAssessmentNote(),
            a.getReviewState().name(),a.getUpdatedAt(),a.getReviewedAt(),a.getReviewedBy(),a.getReviewNote()))
            .orElseGet(()->new Assessment(id,null,null,null,"UNASSESSED",null,null,null,null));
    }
    @Transactional(readOnly=true)
    public Assessment read(String id) {
        var science=resource(id);
        if(!owner(id) && !(curator() && (science.getStatus()==PublicationStatus.IN_REVIEW || science.getStatus()==PublicationStatus.PUBLISHED)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Evaluación no disponible.");
        return view(id);
    }
    private String note(String value,int limit) {
        if(value==null || value.isBlank())return null;
        if(value.length()>limit)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La nota supera la longitud permitida.");
        return value.trim();
    }
    public Assessment save(String id,ScientificPrivacyAssessment.Classification classification,String assessmentNote,Long revision) {
        var science=resource(id);
        if(!owner(id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solo el autor puede evaluar el borrador.");
        if(science.getStatus()!=PublicationStatus.DRAFT)throw new ResponseStatusException(HttpStatus.CONFLICT,"Solo puede evaluarse un borrador editable.");
        if(classification==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indique la clasificación de privacidad.");
        var existing=assessments.findById(id);var assessment=existing.orElseGet(()->new ScientificPrivacyAssessment(id));
        if(existing.isPresent() && (revision==null || revision!=assessment.getRevision()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"La evaluación cambió. Actualice antes de guardar.");
        String normalized=note(assessmentNote,2000);
        if(classification!=ScientificPrivacyAssessment.Classification.NONE && normalized==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explique las medidas de protección sin incluir datos personales.");
        assessment.setClassification(classification);assessment.setAssessmentNote(normalized);assessment.setUpdatedAt(Instant.now());assessment.clearReview();
        assessments.saveAndFlush(assessment);
        events.save(new ScientificRecordEvent(id,username(),"PRIVACY_ASSESSED",classification.name()));
        return view(id);
    }
    public Assessment review(String id,boolean approved,String reviewNote,Long revision) {
        var science=resource(id);
        if(!curator())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"La revisión de privacidad requiere curación.");
        if(science.getStatus()!=PublicationStatus.IN_REVIEW)throw new ResponseStatusException(HttpStatus.CONFLICT,"El depósito debe estar en revisión.");
        var assessment=assessments.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Falta la evaluación del autor."));
        if(revision==null || revision!=assessment.getRevision())throw new ResponseStatusException(HttpStatus.CONFLICT,"La evaluación cambió. Actualice antes de revisar.");
        String normalized=note(reviewNote,1000);
        if(normalized==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indique el criterio de revisión sin incluir datos sensibles.");
        if(approved && assessment.getClassification()!=ScientificPrivacyAssessment.Classification.NONE && !"RESTRICTED".equals(science.getAccessLevel()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Los datos personales/confidenciales requieren acceso restringido. Devuelva el depósito al autor para corregirlo.");
        assessment.setReviewState(approved?ScientificPrivacyAssessment.ReviewState.APPROVED:ScientificPrivacyAssessment.ReviewState.CHANGES_REQUIRED);
        assessment.setReviewedAt(Instant.now());assessment.setReviewedBy(username());assessment.setReviewNote(normalized);assessments.saveAndFlush(assessment);
        events.save(new ScientificRecordEvent(id,username(),approved?"PRIVACY_APPROVED":"PRIVACY_CHANGES_REQUIRED",null));return view(id);
    }
    public void clearReview(String id) {assessments.findById(id).ifPresent(a->{a.clearReview();assessments.save(a);});}
    public void requireSubmissionAllowed(String id,String access) {
        var assessment=assessments.findById(id);
        if(required && assessment.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Complete la evaluación de privacidad antes de enviar.");
        assessment.ifPresent(a->{if(a.getClassification()!=ScientificPrivacyAssessment.Classification.NONE && !"RESTRICTED".equals(access))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Seleccione acceso restringido para datos personales/confidenciales.");});
    }
    public void requirePublicationAllowed(String id,String access) {
        requireSubmissionAllowed(id,access);
        assessments.findById(id).ifPresent(a->{
            if(a.getReviewState()==ScientificPrivacyAssessment.ReviewState.CHANGES_REQUIRED
                || (a.getClassification()!=ScientificPrivacyAssessment.Classification.NONE && a.getReviewState()!=ScientificPrivacyAssessment.ReviewState.APPROVED))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"La evaluación de privacidad necesita aprobación de curación antes de publicar.");
        });
    }
}
