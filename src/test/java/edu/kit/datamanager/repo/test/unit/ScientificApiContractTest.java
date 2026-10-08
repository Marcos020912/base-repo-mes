package edu.kit.datamanager.repo.test.unit;
import edu.kit.datamanager.repo.configuration.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScientificApiContractTest {
    @Test public void separatesSessionPublicCallbackAndReviewerToken(){
        var api=new OpenApiDefinitions().customOpenAPI();api.setPaths(new Paths());
        for(String path:List.of("/api/v1/scientific/{id}/privacy","/api/v1/my-deposit-tasks","/api/v1/public/resources/{id}/versions","/api/v1/scientific/orcid/callback","/api/v1/reviewer/file"))api.getPaths().addPathItem(path,new PathItem().get(new Operation()));
        new ScientificApiContract().customize(api);
        assertEquals("1",api.getInfo().getVersion());assertEquals("soporte@mes.gob.cu",api.getInfo().getContact().getEmail());
        assertTrue(api.getPaths().get("/api/v1/scientific/{id}/privacy").getGet().getSecurity().get(0).containsKey("bearer-jwt"));
        assertTrue(api.getPaths().get("/api/v1/my-deposit-tasks").getGet().getResponses().containsKey("401"));
        assertTrue(api.getPaths().get("/api/v1/public/resources/{id}/versions").getGet().getSecurity().isEmpty());assertTrue(api.getPaths().get("/api/v1/scientific/orcid/callback").getGet().getSecurity().isEmpty());
        assertTrue(api.getPaths().get("/api/v1/reviewer/file").getGet().getSecurity().get(0).containsKey("reviewer-token"));assertEquals("token",api.getComponents().getSecuritySchemes().get("reviewer-token").getName());
    }
    @Test public void documentsBinaryCitationAndTombstone(){
        var api=new OpenApiDefinitions().customOpenAPI();api.setPaths(new Paths());
        var citation=new Operation().addParametersItem(new Parameter().name("format"));
        api.getPaths().addPathItem("/api/v1/public/resources/{id}/archive",new PathItem().get(new Operation()));api.getPaths().addPathItem("/api/v1/public/resources/{id}",new PathItem().get(new Operation()));api.getPaths().addPathItem("/api/v1/scientific/{id}/citation",new PathItem().get(citation));new ScientificApiContract().customize(api);
        var binary=api.getPaths().get("/api/v1/public/resources/{id}/archive").getGet().getResponses().get("200");assertEquals("binary",binary.getContent().get("application/zip").getSchema().getFormat());assertTrue(binary.getHeaders().containsKey("Content-Disposition"));assertTrue(citation.getResponses().get("200").getContent().containsKey("application/json"));assertTrue(citation.getParameters().get(0).getSchema().getEnum().contains("bibtex"));assertTrue(api.getPaths().get("/api/v1/public/resources/{id}").getGet().getResponses().containsKey("410"));
    }
    @Test public void identifiersDoNotDependOnRegistrationOrder(){
        String target="/api/v1/scientific/{id}/privacy";
        var first=new OpenApiDefinitions().customOpenAPI();first.setPaths(new Paths());
        first.getPaths().addPathItem(target,new PathItem().get(new Operation()).put(new Operation()));
        new ScientificApiContract().customize(first);
        String identifier=first.getPaths().get(target).getGet().getOperationId();
        var second=new OpenApiDefinitions().customOpenAPI();second.setPaths(new Paths());
        second.getPaths().addPathItem("/api/v1/scientific/new-operation",new PathItem().get(new Operation()));
        second.getPaths().addPathItem(target,new PathItem().put(new Operation()).get(new Operation()));
        new ScientificApiContract().customize(second);
        assertEquals(identifier,second.getPaths().get(target).getGet().getOperationId());
        assertNotEquals(identifier,second.getPaths().get(target).getPut().getOperationId());
    }
}
