package edu.kit.datamanager.repo.configuration;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.headers.Header;
import java.util.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

/** Document actual transport boundaries without changing runtime authorization. */
@Configuration
public class ScientificApiContract {
    @Bean public OpenApiCustomizer scientificApiContractCustomizer(){return this::customize;}
    public void customize(OpenAPI api){
        if(api.getComponents()==null)api.setComponents(new Components());
        api.getComponents().addSecuritySchemes("reviewer-token",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.QUERY).name("token").description("Enlace de revisión temporal y revocable. No es una sesión de usuario; no comparta ni registre el token."));
        if(api.getPaths()==null)return;
        api.getPaths().forEach((path,item)->item.readOperationsMap().forEach((method,operation)->{
            boolean publicPath=(method==PathItem.HttpMethod.GET && (path.startsWith("/api/v1/public/") || path.equals("/api/v1/scientific/orcid/callback") || path.equals("/api/v1/scientific/{id}/citation"))) ||
                    (method==PathItem.HttpMethod.POST && Set.of("/api/v1/auth/login","/api/v1/auth/register","/api/v1/auth/verify","/api/v1/auth/resend-verification").contains(path));
            boolean reviewer=method==PathItem.HttpMethod.GET && path.startsWith("/api/v1/reviewer/");
            boolean privatePath=path.startsWith("/api/v1/")&&!publicPath&&!reviewer;
            if(publicPath)operation.setSecurity(List.of());
            else if(reviewer)operation.setSecurity(List.of(new SecurityRequirement().addList("reviewer-token")));
            else if(privatePath)operation.setSecurity(List.of(new SecurityRequirement().addList("bearer-jwt")));
            if(!(publicPath||reviewer||privatePath))return;
            operation.setOperationId("scientific_"+method.name().toLowerCase(java.util.Locale.ROOT)+"_"+path.replaceAll("[^A-Za-z0-9]+","_")+"_"+Integer.toUnsignedString(path.hashCode(),16));
            if(operation.getResponses()==null)operation.setResponses(new ApiResponses());
            for(var error:Map.of("400","Parámetros o metadatos no válidos.","403","Operación no autorizada por permisos o política de acceso.","404","Recurso no disponible o no visible.","409","Conflicto de estado, concurrencia o requisito de publicación.","503","Servicio o consulta temporalmente no disponible.").entrySet())operation.getResponses().putIfAbsent(error.getKey(),new ApiResponse().description(error.getValue()));
            if(privatePath&&!publicPath)operation.getResponses().putIfAbsent("401",new ApiResponse().description("Se requiere una sesión válida de una cuenta verificada y activa."));
            if(path.equals("/api/v1/public/resources/{id}"))operation.getResponses().putIfAbsent("410",new ApiResponse().description("Ficha permanente de una versión retirada; no contiene archivos descargables.").content(new Content().addMediaType("application/json",new MediaType().schema(new ObjectSchema().addProperty("title",new StringSchema()).addProperty("doi",new StringSchema()).addProperty("reason",new StringSchema())))));
            if(path.equals("/api/v1/scientific/orcid/callback")){operation.getResponses().remove("200");operation.getResponses().put("303",new ApiResponse().description("Redirección a la ficha tras finalizar el consentimiento ORCID.").addHeaderObject("Location",new Header().schema(new StringSchema())));}
            String binaryMime=path.equals("/api/v1/public/resources/{id}/archive")||path.equals("/api/v1/scientific/preservation/{id}/package")?"application/zip":path.equals("/api/v1/public/resources/{id}/file")||path.equals("/api/v1/reviewer/file")?"application/octet-stream":null;
            if(binaryMime!=null)operation.getResponses().put("200",new ApiResponse().description("Contenido binario; el acceso se valida antes de iniciar la transferencia.").content(new Content().addMediaType(binaryMime,new MediaType().schema(new StringSchema().format("binary")))).addHeaderObject("Content-Disposition",new Header().description("Nombre seguro para guardar el archivo.").schema(new StringSchema())));
            if("application/octet-stream".equals(binaryMime))operation.getResponses().get("200").getContent().addMediaType("*/*",new MediaType().schema(new StringSchema().format("binary")));
            if(path.endsWith("/citation")){
                operation.getResponses().put("200",new ApiResponse().description("Cita de la versión exacta; texto para estilos/BibTeX/RIS y JSON para CSL.").content(new Content().addMediaType("text/plain",new MediaType().schema(new StringSchema())).addMediaType("application/json",new MediaType().schema(new ObjectSchema()))));
                if(operation.getParameters()!=null)operation.getParameters().stream().filter(p->"format".equals(p.getName())).forEach(p->{var schema=new StringSchema();schema.setEnum(List.of("apa","vancouver","chicago","ieee","bibtex","ris","csl-json"));schema.setDefault("apa");p.setSchema(schema);});
            }
        }));
    }
}
