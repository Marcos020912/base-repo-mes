package edu.kit.datamanager.repo.test.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.ResourceType;
import edu.kit.datamanager.repo.service.ResourceTypeTransitionPolicy;
import org.junit.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;

public class ResourceTypeTransitionPolicyTest {
    private DataResource resource(ResourceType.TYPE_GENERAL type) {
        var resource=DataResource.factoryNewDataResource("r1");
        resource.setResourceType(ResourceType.createResourceType(type.name(),type));return resource;
    }
    private JsonPatch patch(String json) throws Exception {return JsonPatch.fromJson(new ObjectMapper().readTree(json));}
    @Test public void allTypesAllowOnlySameTypeOrOther() {
        for(var before:ResourceType.TYPE_GENERAL.values())for(var after:ResourceType.TYPE_GENERAL.values()) {
            var current=resource(before).getResourceType();var proposed=resource(after).getResourceType();
            if(before==after || after==ResourceType.TYPE_GENERAL.OTHER) ResourceTypeTransitionPolicy.validate(current,proposed);
            else assertEquals(400,assertThrows(ResponseStatusException.class,()->ResourceTypeTransitionPolicy.validate(current,proposed)).getStatusCode().value());
        }
    }
    @Test public void patchPreviewDoesNotMutateResourceAndCannotBypassTypeRule() throws Exception {
        var image=resource(ResourceType.TYPE_GENERAL.IMAGE);
        ResourceTypeTransitionPolicy.validatePatch(image,patch("[{\"op\":\"replace\",\"path\":\"/resourceType/typeGeneral\",\"value\":\"OTHER\"}]"));
        assertEquals(ResourceType.TYPE_GENERAL.IMAGE,image.getResourceType().getTypeGeneral());
        for(String json:java.util.List.of(
                "[{\"op\":\"replace\",\"path\":\"/resourceType/typeGeneral\",\"value\":\"TEXT\"}]",
                "[{\"op\":\"remove\",\"path\":\"/resourceType\"}]",
                "[{\"op\":\"replace\",\"path\":\"/resourceType\",\"value\":{\"typeGeneral\":\"DATASET\"}}]",
                "[{\"op\":\"replace\",\"path\":\"\",\"value\":{\"resourceType\":{\"typeGeneral\":\"TEXT\"}}}]",
                "[{\"op\":\"replace\",\"path\":\"/publisher\",\"value\":\"TEXT\"},{\"op\":\"copy\",\"from\":\"/publisher\",\"path\":\"/resourceType/typeGeneral\"}]"))
            assertEquals(400,assertThrows(ResponseStatusException.class,()->ResourceTypeTransitionPolicy.validatePatch(image,patch(json))).getStatusCode().value());
        assertEquals(ResourceType.TYPE_GENERAL.IMAGE,image.getResourceType().getTypeGeneral());
    }
}
