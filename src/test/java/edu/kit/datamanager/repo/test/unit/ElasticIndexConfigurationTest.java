package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.elastic.ElasticWrapper;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public class ElasticIndexConfigurationTest {
    @Test public void defaultsToLegacyIndex() { assertIndex(Map.of(), "baserepo"); }
    @Test public void honorsConfiguredIndexForRepositoryWrites() {
        assertIndex(Map.of("repo.search.index", "baserepo-local-demo"), "baserepo-local-demo");
    }
    private void assertIndex(Map<String,Object> properties, String expected) {
        try (var context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture", properties));
            context.refresh();
            var mapping = new SimpleElasticsearchMappingContext();
            mapping.setApplicationContext(context);
            assertEquals(expected, mapping.getRequiredPersistentEntity(ElasticWrapper.class).getIndexCoordinates().getIndexName());
        }
    }
}
