package edu.kit.datamanager.repo.test.unit;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.datamanager.repo.web.impl.ScientificCatalogController.CatalogPage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class CatalogEmptySerializationTest {
    @Test public void emptyItemsSurviveNonEmptySerialization() throws Exception {
        var mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        var json = mapper.readTree(mapper.writeValueAsString(new CatalogPage(List.of(),0,0,0)));
        assertNotNull(json.get("items"));
        assertTrue(json.get("items").isArray());
        assertEquals(0,json.get("items").size());
        assertEquals(0,json.get("total").asLong());
    }
}
