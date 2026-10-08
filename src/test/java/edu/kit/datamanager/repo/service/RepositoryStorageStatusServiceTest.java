package edu.kit.datamanager.repo.service;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RepositoryStorageStatusServiceTest {
    @Test public void measuresLocalVolumeWithoutExposingPath() throws Exception {
        Path dir=Files.createTempDirectory("reduniv-storage-test");try{
            var status=new RepositoryStorageStatusService(dir.toUri().toString()).status();
            assertEquals("AVAILABLE",status.status());assertNotNull(status.measuredAt());assertTrue(status.totalBytes()>0);assertTrue(status.usableBytes()>=0);assertTrue(status.unallocatedBytes()>=0);assertFalse(status.toString().contains(dir.toString()));assertTrue(status.scope().contains("otros servicios"));
        }finally{Files.delete(dir);}
    }
    @Test public void unsupportedAndMissingAreNotZeroCapacity(){var remote=new RepositoryStorageStatusService("s3://bucket/path").status();assertEquals("UNSUPPORTED",remote.status());assertNull(remote.totalBytes());var invalid=new RepositoryStorageStatusService("file:").status();assertEquals("UNAVAILABLE",invalid.status());assertNull(invalid.usableBytes());var missing=new RepositoryStorageStatusService(Path.of("/tmp",java.util.UUID.randomUUID().toString()).toUri().toString()).status();assertEquals("UNAVAILABLE",missing.status());}
}
