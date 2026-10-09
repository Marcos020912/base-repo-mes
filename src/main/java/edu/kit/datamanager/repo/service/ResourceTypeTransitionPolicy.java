package edu.kit.datamanager.repo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ResourceType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A dataset type may only stay unchanged or transition once to OTHER. */
public final class ResourceTypeTransitionPolicy {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private ResourceTypeTransitionPolicy() {}

    public static void validate(ResourceType current, ResourceType proposed) {
        var before = current == null ? null : current.getTypeGeneral();
        var after = proposed == null ? null : proposed.getTypeGeneral();
        validate(before, after);
    }
    private static void validate(ResourceType.TYPE_GENERAL before, ResourceType.TYPE_GENERAL after) {
        if (before == after) return;
        if (after == null || before != null && before != after && after != ResourceType.TYPE_GENERAL.OTHER)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El tipo solo puede cambiarse una vez a OTHER.");
    }

    /** Apply every operation, including move/copy/root replacement, to a detached JSON preview. */
    public static void validatePatch(DataResource existing, JsonPatch patch) {
        try {
            var original = JSON.valueToTree(existing);
            var preview = patch.apply(original);
            if (original.path("resourceType").equals(preview.path("resourceType"))) return;
            var value = preview.path("resourceType").path("typeGeneral");
            var after = value.isTextual() ? ResourceType.TYPE_GENERAL.valueOf(value.asText()) : null;
            validate(existing.getResourceType() == null ? null : existing.getResourceType().getTypeGeneral(), after);
        } catch (JsonPatchException invalid) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Parche de metadatos no válido.");
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Parche de metadatos no válido.");
        }
    }
}
