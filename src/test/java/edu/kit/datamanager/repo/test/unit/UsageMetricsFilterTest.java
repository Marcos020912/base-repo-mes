package edu.kit.datamanager.repo.test.unit;
import edu.kit.datamanager.repo.security.UsageMetricsFilter;
import edu.kit.datamanager.repo.service.UsageMetricsService;
import edu.kit.datamanager.repo.domain.UsageObservation;
import org.junit.*;
import org.springframework.mock.web.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
public class UsageMetricsFilterTest {
 private final UsageMetricsService metrics=mock(UsageMetricsService.class);
 @Before public void setup(){when(metrics.enabled()).thenReturn(true);}
 private void request(String method,String suffix,String agent,int status,boolean inline,boolean fail)throws Exception{
  var request=new MockHttpServletRequest(method,"/api/v1/public/resources/r"+suffix);request.setServletPath(request.getRequestURI());request.addHeader("User-Agent",agent);if(inline)request.addParameter("inline","true");
  var response=new MockHttpServletResponse();new UsageMetricsFilter(metrics).doFilter(request,response,(req,res)->{response.setStatus(status);if(fail)throw new java.io.IOException("aborted");});
 }
 @Test public void countsSuccessfulViewAndDownloadOnly()throws Exception{
  request("GET","","Mozilla/5.0",200,false,false);request("GET","/file","Mozilla/5.0",206,false,false);
  verify(metrics).record(eq("r"),eq(UsageObservation.Kind.VIEW),eq("detail"),anyString(),eq("Mozilla/5.0"),any());
  verify(metrics).record(eq("r"),eq(UsageObservation.Kind.DOWNLOAD),eq("/file:"),anyString(),eq("Mozilla/5.0"),any());
 }
 @Test public void excludesErrorsHeadRobotsInlineAndOtherRoutes()throws Exception{
  request("GET","","Mozilla/5.0",404,false,false);request("HEAD","","Mozilla/5.0",200,false,false);
  request("GET","","Googlebot",200,false,false);request("GET","/file","Mozilla/5.0",200,true,false);
  request("GET","/files","Mozilla/5.0",200,false,false);verify(metrics,never()).record(anyString(),any(),anyString(),anyString(),anyString(),any());
 }
 @Test public void abortedResponseDoesNotCount()throws Exception{
  org.junit.Assert.assertThrows(java.io.IOException.class,()->request("GET","/archive","Mozilla/5.0",200,false,true));
  verify(metrics,never()).record(anyString(),any(),anyString(),anyString(),anyString(),any());
 }
 @Test public void failedMetricsDoNotBreakDownloads()throws Exception{
  doThrow(new IllegalStateException()).when(metrics).record(anyString(),any(),anyString(),anyString(),anyString(),any());request("GET","/archive","Mozilla/5.0",200,false,false);
 }
}
