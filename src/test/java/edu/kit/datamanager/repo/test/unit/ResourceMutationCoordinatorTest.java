package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.service.ResourceMutationCoordinator;
import java.util.concurrent.*;
import org.junit.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;

public class ResourceMutationCoordinatorTest {
    private ResourceMutationCoordinator local() {
        var database=new DataSourceProperties();database.setUrl("jdbc:h2:mem:coordinator-test");
        return new ResourceMutationCoordinator(database,4);
    }
    @Test public void resourceLeaseExcludesSecondThreadButNotOtherDataset() throws Exception {
        try(CoordinatorHolder coordinator=new CoordinatorHolder(local())) {
            var held=coordinator.value.acquire("r1");
            ExecutorService worker=Executors.newSingleThreadExecutor();
            try {
                assertEquals(409,worker.submit(()->assertThrows(ResponseStatusException.class,()->coordinator.value.acquire("r1")).getStatusCode().value()).get().intValue());
                try(var other=coordinator.value.acquire("r2")) {}
                held.close();
                var next=coordinator.value.acquire("r1");held.close();
                assertThrows(ResponseStatusException.class,()->coordinator.value.acquire("r1"));
                next.close();try(var retry=coordinator.value.acquire("r1")) {}
            } finally {held.close();worker.shutdownNow();}
        }
    }
    @Test public void hashedKeyIsStableAndNamespaced() {
        assertEquals(ResourceMutationCoordinator.key("r1"),ResourceMutationCoordinator.key("r1"));
        assertNotEquals(ResourceMutationCoordinator.key("r1"),ResourceMutationCoordinator.key("r2"));
    }
    @Test public void unsupportedDatabaseCannotSilentlyUseLocalMutex() {
        for(String url:java.util.List.of("jdbc:mysql://localhost/repository","jdbc:h2:tcp://localhost/repository","jdbc:h2:mem:test;AUTO_SERVER=TRUE")) {
            var database=new DataSourceProperties();database.setUrl(url);
            assertThrows(IllegalArgumentException.class,()->new ResourceMutationCoordinator(database,4));
        }
    }
    private record CoordinatorHolder(ResourceMutationCoordinator value) implements AutoCloseable {public void close(){value.close();}}
}
