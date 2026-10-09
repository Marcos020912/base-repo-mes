package edu.kit.datamanager.repo.test.unit;

import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.service.RepositoryFileAccess;
import edu.kit.datamanager.repo.util.ContentDataUtils;
import edu.kit.datamanager.repo.util.DataResourceUtils;
import edu.kit.datamanager.repo.web.impl.DatasetArchiveController;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DatasetArchiveControllerTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private ContentInformation file(Path root,String name) throws Exception {
        Path path=root.resolve(name);Files.createDirectories(path.getParent());Files.writeString(path,"content:"+name);
        var info=mock(ContentInformation.class);when(info.getRelativePath()).thenReturn(name);when(info.getContentUri()).thenReturn(path.toUri().toString());return info;
    }
    @Test public void pagesAllFilesAndProducesReadableZip() throws Exception {
        Path root=folder.newFolder().toPath();var repository=mock(RepoBaseConfiguration.class);var resource=mock(DataResource.class);
        List<ContentInformation> all=new ArrayList<>();all.add(file(root,"description/description.md"));
        for(int i=0;i<100;i++)all.add(file(root,"file-"+i+".csv"));
        List<Integer> pages=new ArrayList<>();
        try(var resourceUtils=mockStatic(DataResourceUtils.class);var contentUtils=mockStatic(ContentDataUtils.class)) {
            resourceUtils.when(()->DataResourceUtils.getResourceByIdentifierOrRedirect(eq(repository),eq("r1"),isNull(),any())).thenReturn(resource);
            contentUtils.when(()->ContentDataUtils.readFiles(eq(repository),eq(resource),eq(""),isNull(),isNull(),any(),any())).thenAnswer(call->{
                Pageable request=call.getArgument(5);assertTrue(request.isPaged());assertEquals(100,request.getPageSize());
                assertEquals("id",request.getSort().iterator().next().getProperty());pages.add(request.getPageNumber());
                int start=request.getPageNumber()*100;return all.subList(start,Math.min(start+100,all.size()));
            });
            var response=new MockHttpServletResponse();new DatasetArchiveController(repository,new RepositoryFileAccess(root.toUri().toString())).download("r1",response);
            assertEquals(List.of(0,1),pages);assertEquals("application/zip",response.getContentType());
            Set<String> names=new HashSet<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){assertTrue(names.add(entry.getName()));assertEquals("content:"+entry.getName(),new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            }
            assertEquals(101,names.size());assertTrue(names.contains("description/description.md"));
        }
    }
    @Test public void rejectsInvalidFileBeforeWritingHeadersOrZip() throws Exception {
        Path root=folder.newFolder().toPath();var repository=mock(RepoBaseConfiguration.class);var resource=mock(DataResource.class);
        var info=file(root,"valid.csv");when(info.getRelativePath()).thenReturn("../outside.csv");
        try(var resourceUtils=mockStatic(DataResourceUtils.class);var contentUtils=mockStatic(ContentDataUtils.class)) {
            resourceUtils.when(()->DataResourceUtils.getResourceByIdentifierOrRedirect(eq(repository),eq("r1"),isNull(),any())).thenReturn(resource);
            contentUtils.when(()->ContentDataUtils.readFiles(eq(repository),eq(resource),eq(""),isNull(),isNull(),any(),any())).thenReturn(List.of(info));
            var response=new MockHttpServletResponse();
            assertEquals(409,assertThrows(ResponseStatusException.class,()->new DatasetArchiveController(repository,new RepositoryFileAccess(root.toUri().toString())).download("r1",response)).getStatusCode().value());
            assertFalse(response.isCommitted());assertNull(response.getContentType());assertEquals(0,response.getContentAsByteArray().length);
        }
    }
}
