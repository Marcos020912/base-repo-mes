package edu.kit.datamanager.repo.security;

import edu.kit.datamanager.repo.service.UsageMetricsService;
import edu.kit.datamanager.repo.domain.UsageObservation;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(Ordered.LOWEST_PRECEDENCE)
public class UsageMetricsFilter extends OncePerRequestFilter {
    private static final Pattern PATH=Pattern.compile("^/api/v1/public/resources/([^/]+)(/file|/archive)?$");
    private static final Pattern ROBOT=Pattern.compile("bot|crawler|spider|slurp|headless|curl|wget|python|scrapy|datacite|preview|^node$|java-http-client",Pattern.CASE_INSENSITIVE);
    private final UsageMetricsService metrics;
    public UsageMetricsFilter(UsageMetricsService metrics){this.metrics=metrics;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var match=PATH.matcher(request.getServletPath());
        String agent=request.getHeader("User-Agent");
        boolean eligible=metrics.enabled()&&"GET".equals(request.getMethod())&&match.matches()&&agent!=null&&!agent.isBlank()
            &&!ROBOT.matcher(agent).find()&&!"true".equalsIgnoreCase(request.getParameter("inline"))
            &&!"prefetch".equalsIgnoreCase(request.getHeader("Purpose"))
            &&(request.getHeader("Sec-Purpose")==null||!request.getHeader("Sec-Purpose").toLowerCase(Locale.ROOT).contains("prefetch"));
        chain.doFilter(request,response);
        if(!eligible||request.isAsyncStarted()||response.getStatus()!=200&&response.getStatus()!=206)return;
        var kind=match.group(2)==null?UsageObservation.Kind.VIEW:UsageObservation.Kind.DOWNLOAD;
        String link=match.group(2)==null?"detail":match.group(2)+":"+java.util.Objects.toString(request.getParameter("path"),"");
        try {metrics.record(match.group(1),kind,link,request.getRemoteAddr(),agent,Instant.now());}
        catch(RuntimeException ignored){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Usage observation not recorded; response remains unaffected.");}
    }
}
