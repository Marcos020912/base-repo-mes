/*
 * Copyright 2016 Karlsruhe Institute of Technology.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.kit.datamanager.repo.web.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import edu.kit.datamanager.entities.PERMISSION;
import edu.kit.datamanager.entities.RepoUserRole;
import edu.kit.datamanager.exceptions.CustomInternalServerError;
import edu.kit.datamanager.repo.configuration.ApplicationProperties;
import edu.kit.datamanager.repo.configuration.RepoBaseConfiguration;
import edu.kit.datamanager.repo.dao.IContentInformationDao;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.WebRequest;
import edu.kit.datamanager.repo.domain.ContentInformation;
import edu.kit.datamanager.repo.domain.DataResource;
import edu.kit.datamanager.repo.domain.TabulatorLocalPagination;
import edu.kit.datamanager.repo.domain.acl.AclEntry;
import edu.kit.datamanager.repo.domain.ResourceOwnership;
import edu.kit.datamanager.repo.domain.FileProvenanceEvent;
import edu.kit.datamanager.repo.repository.ResourceOwnershipRepository;
import edu.kit.datamanager.repo.repository.FileProvenanceEventRepository;
import edu.kit.datamanager.repo.repository.FileFixityStateRepository;
import edu.kit.datamanager.repo.repository.ScientificRecordRepository;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import edu.kit.datamanager.repo.elastic.DataResourceRepository;
import edu.kit.datamanager.repo.elastic.ElasticWrapper;
import edu.kit.datamanager.repo.service.IContentInformationService;
import edu.kit.datamanager.repo.util.ContentDataUtils;
import edu.kit.datamanager.repo.util.DataResourceUtils;
import edu.kit.datamanager.repo.util.EntityUtils;
import edu.kit.datamanager.repo.web.IDataResourceController;
import edu.kit.datamanager.service.IAuditService;
import edu.kit.datamanager.util.AuthenticationHelper;
import edu.kit.datamanager.util.ControllerUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Controller for data resource endpoints.
 *
 * @author jejkal
 */
@Controller
@RequestMapping(value = "/api/v1/dataresources")
@Schema(description = "Data Resource Management")
public class DataResourceController implements IDataResourceController {

    public static final String VERSION_HEADER = "Resource-Version";
    public static final String CONTENT_RANGE_HEADER = "Content-Range";
    // private final JsonResult json = JsonResult.instance();
    private final Logger LOGGER = LoggerFactory.getLogger(DataResourceController.class);

    private final IContentInformationService contentInformationService;

    @Autowired
    private final ApplicationProperties applicationProperties;

    @Autowired
    private IDataResourceDao dataResourceDao;
    @Autowired
    private IContentInformationDao contentInformationDao;

    private final IAuditService<DataResource> auditService;
    private final IAuditService<ContentInformation> contentAuditService;
    private final RepoBaseConfiguration repositoryProperties;
    @Autowired
    private Optional<DataResourceRepository> dataResourceRepository;
    @Autowired
    private edu.kit.datamanager.repo.service.SearchIndexOutbox searchOutbox;
    @Autowired
    private edu.kit.datamanager.repo.service.ScientificResourceWriteLock writeLock;
    @Autowired
    private ResourceOwnershipRepository ownershipRepository;
    @Autowired
    private ScientificRecordRepository scientificRecords;
    @Autowired(required = false)
    private FileProvenanceEventRepository fileProvenance;
    @Autowired(required = false)
    private FileFixityStateRepository fileFixityStates;
    @Value("${repo.catalog.shared-read:false}")
    private boolean sharedRead;

    /**
     * Default constructor.
     *
     * @param applicationProperties Properties object.
     * @param repositoryConfig Generic configuratation object.
     */
    public DataResourceController(ApplicationProperties applicationProperties,
            RepoBaseConfiguration repositoryConfig) {
        this.applicationProperties = applicationProperties;
        this.contentInformationService = repositoryConfig.getContentInformationService();
        auditService = repositoryConfig.getAuditService();
        contentAuditService = repositoryConfig.getContentInformationAuditService();
        repositoryProperties = repositoryConfig;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<DataResource> create(@RequestBody final DataResource resource,
            final WebRequest request,
            final HttpServletResponse response) {

        LOGGER.trace("Creating resource with record '{}'.", resource);
        Function<String, String> getById;
        getById = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).getById(t, 1l, request, response)).toString();
        };

        LOGGER.trace("Removing user-provided @Ids from resource.");
        EntityUtils.removeIds(resource);
        if (sharedRead) resource.getAcls().add(new AclEntry(AuthenticationHelper.ANONYMOUS_USER_PRINCIPAL, PERMISSION.READ));

        DataResource result = DataResourceUtils.createResource(repositoryProperties, resource);
        ownershipRepository.save(new ResourceOwnership(result.getId(), AuthenticationHelper.getPrincipal()));
        scientificRecords.save(new ScientificRecord(result.getId()));
        try {
            LOGGER.trace("Creating controller link for resource identifier {}.", result.getId());
            //do some hacking in order to properly escape the resource identifier
            //if escaping in beforehand, WebMvcLinkBuilder will escape again, which invalidated the link
            String uriLink = getById.apply("WorkaroundPlaceholder");
            //replace placeholder with escaped identifier in order to ensure single-escaping
            uriLink = uriLink.replaceFirst("WorkaroundPlaceholder", URLEncoder.encode(result.getId(), "UTF-8"));
            // remove version flag if version is nor supported
            if (!applicationProperties.isAuditEnabled()) {
                // Remove path parameter version
                int qmIndex = uriLink.lastIndexOf("?");
                if (qmIndex > 0) {
                    uriLink = uriLink.substring(0, qmIndex);
                }
            }

            indexResource(resource.getId(), false);

            LOGGER.trace("Created resource link is: {}", uriLink);
            return ResponseEntity.created(URI.create(uriLink)).eTag("\"" + result.getEtag() + "\"").header(VERSION_HEADER, Long.toString(1l)).body(result);
        } catch (UnsupportedEncodingException ex) {
            LOGGER.error("Failed to encode resource identifier " + result.getId() + ".", ex);
            throw new CustomInternalServerError("Failed to decode resource identifier " + result.getId() + ", but resource has been created.");
        }
    }

    @Override
    public ResponseEntity<DataResource> getById(@PathVariable("id") final String identifier,
            @RequestParam(name = "version", required = false) final Long version,
            final WebRequest request,
            final HttpServletResponse response) {
        LOGGER.trace("Get resource by id '{}' and version '{}'.", identifier, version);
        Function<String, String> getById = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).getById(t, version, request, response)).toString();
        };
        return DataResourceUtils.readResource(repositoryProperties, identifier, version, getById);
    }

    @Override
    public ResponseEntity<DataResource> getByPid(@PathVariable("prefix") final String prefix,
            @PathVariable("suffix") final String suffix,
            @RequestParam(name = "version", required = false) final Long version,
            final WebRequest request,
            final HttpServletResponse response) {
        return getById(prefix + "/" + suffix, version, request, response);
    }

    @Override
    public ResponseEntity<List<DataResource>> findAll(@RequestParam(name = "from", required = false) final Instant lastUpdateFrom,
            @RequestParam(name = "until", required = false) final Instant lastUpdateUntil,
            final Pageable pgbl,
            final WebRequest request,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        return findByExample(null, lastUpdateFrom, lastUpdateUntil, pgbl, request, response, uriBuilder);
    }

    @Override
    public ResponseEntity<TabulatorLocalPagination> findAllForTabulator(@RequestParam(name = "from", required = false) final Instant lastUpdateFrom,
            @RequestParam(name = "until", required = false) final Instant lastUpdateUntil,
            final Pageable pgbl,
            final WebRequest request,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        //change from 1-based to 0-based index as required by tabulator
        int startPage = pgbl.getPageNumber() - 1;
        startPage = startPage < 0 ? 0 : startPage;
        PageRequest pr = PageRequest.of(startPage, pgbl.getPageSize(), pgbl.getSort());
        Page<DataResource> page = DataResourceUtils.readAllResourcesFilteredByExample(repositoryProperties, null, lastUpdateFrom, lastUpdateUntil, pr, response, uriBuilder);
        PageRequest pageRequest = ControllerUtils.checkPaginationInformation(pgbl);
        response.addHeader(CONTENT_RANGE_HEADER, ControllerUtils.getContentRangeHeader(page.getNumber(), pageRequest.getPageSize(), page.getTotalElements()));
        TabulatorLocalPagination tabulatorLocalPagination = TabulatorLocalPagination.builder()
                .lastPage(page.getTotalPages())
                //.data(DataResourceUtils.filterResources(page.getContent()))
                .data(page.getContent())
                .build();
        return ResponseEntity.ok().body(tabulatorLocalPagination);
    }

    @Override
    public ResponseEntity<List<DataResource>> findByExample(@RequestBody DataResource example,
            @RequestParam(name = "from", required = false) final Instant lastUpdateFrom,
            @RequestParam(name = "until", required = false) final Instant lastUpdateUntil,
            final Pageable pgbl,
            final WebRequest req,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        LOGGER.trace("Find resource by example '{}' from '{}' until '{}'", example, lastUpdateFrom, lastUpdateUntil);
        Page<DataResource> page = DataResourceUtils.readAllResourcesFilteredByExample(repositoryProperties, example, lastUpdateFrom, lastUpdateUntil, pgbl, response, uriBuilder);

        //set content-range header for react-admin (index_start-index_end/total
        PageRequest request = ControllerUtils.checkPaginationInformation(pgbl);
        response.addHeader(CONTENT_RANGE_HEADER, ControllerUtils.getContentRangeHeader(page.getNumber(), request.getPageSize(), page.getTotalElements()));
       // return ResponseEntity.ok().body(DataResourceUtils.filterResources(page.getContent()));
        return ResponseEntity.ok().body(page.getContent());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity patch(@PathVariable("id") final String identifier,
            @RequestBody final JsonPatch patch,
            final WebRequest request,
            final HttpServletResponse response) {
        LOGGER.trace("Patch resource with id '{}': Patch '{}'", identifier, patch);
        Function<String, String> patchDataResource = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).patch(t, patch, request, response)).toString();
        };
        //String path = ContentDataUtils.getContentPathFromRequest(request);
        String eTag = ControllerUtils.getEtagFromHeader(request);
        if (repositoryProperties.isReadOnly()) throw new edu.kit.datamanager.exceptions.ServiceUnavailableException("Repository is in read-only mode. Patch request denied.");
        ControllerUtils.checkAnonymousAccess();
        DataResource existing = DataResourceUtils.getResourceByIdentifierOrRedirect(repositoryProperties, identifier, null, patchDataResource);
        DataResourceUtils.performPermissionCheck(existing, PERMISSION.WRITE);
        existing = writeLock.acquireEditable(existing.getId());
        ControllerUtils.checkEtag(eTag, existing);
        edu.kit.datamanager.repo.service.ResourceTypeTransitionPolicy.validatePatch(existing, patch);
        DataResourceUtils.patchResource(repositoryProperties, identifier, patch, eTag, patchDataResource);

        indexResource(identifier, true);

        long currentVersion = auditService.getCurrentVersion(identifier);
        if (currentVersion > 0) {
            return ResponseEntity.noContent().header(VERSION_HEADER, Long.toString(currentVersion)).build();
        } else {
            return ResponseEntity.noContent().build();

        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity patchPid(@PathVariable("prefix") final String prefix,
            @PathVariable("suffix") final String suffix,
            @RequestBody final JsonPatch patch,
            final WebRequest request,
            final HttpServletResponse response) {
        return patch(prefix + "/" + suffix, patch, request, response);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity put(@PathVariable("id") final String identifier,
            @RequestBody final DataResource newResource,
            final WebRequest request,
            final HttpServletResponse response) {
        LOGGER.trace("Update resource with id '{}': new resource: '{}'", identifier, newResource);
        DataResource existing = dataResourceDao.findById(identifier).orElse(null);
        if (existing != null) existing = writeLock.acquireEditable(existing.getId());
        if (existing != null) edu.kit.datamanager.repo.service.ResourceTypeTransitionPolicy.validate(
                existing.getResourceType(), newResource.getResourceType());
        Function<String, String> putWithId;
        putWithId = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).put(t, newResource, request, response)).toString();
        };
        DataResource result = DataResourceUtils.updateResource(repositoryProperties, identifier, newResource, request, putWithId);

        indexResource(identifier, true);

        long currentVersion = repositoryProperties.getAuditService().getCurrentVersion(result.getId());

        if (currentVersion > 0) {
            //trigger response creation and set etag...the response body is set automatically
            //return ResponseEntity.ok().eTag("\"" + result.getEtag() + "\"").header(VERSION_HEADER, Long.toString(currentVersion)).body(DataResourceUtils.filterResource(result));
            return ResponseEntity.ok().eTag("\"" + result.getEtag() + "\"").header(VERSION_HEADER, Long.toString(currentVersion)).body(result);
        } else {
            //return ResponseEntity.ok().eTag("\"" + result.getEtag() + "\"").body(DataResourceUtils.filterResource(result));
            return ResponseEntity.ok().eTag("\"" + result.getEtag() + "\"").body(result);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity putPid(@PathVariable("prefix") final String prefix,
            @PathVariable("suffix") final String suffix,
            @RequestBody final DataResource newResource,
            final WebRequest request,
            final HttpServletResponse response) {
        return put(prefix + "/" + suffix, newResource, request, response);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity delete(@PathVariable("id") final String identifier,
            final WebRequest request,
            final HttpServletResponse response) {
        LOGGER.trace("Delete resource with id '{}'", identifier);
        Function<String, String> getById = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).getById(t, 1l, request, response)).toString();
        };
        ControllerUtils.getEtagFromHeader(request);
        if (repositoryProperties.isReadOnly()) throw new edu.kit.datamanager.exceptions.ServiceUnavailableException("Repository is in read-only mode.");
        DataResource deleting = dataResourceDao.findById(identifier).orElse(null);
        if (deleting != null) writeLock.acquireEditable(deleting.getId());
        DataResourceUtils.deleteResource(repositoryProperties, identifier, request, getById);
        scientificRecords.findById(identifier).ifPresent(scientificRecords::delete);
        ownershipRepository.findById(identifier).ifPresent(ownershipRepository::delete);

        unindexResource(identifier);

        return ResponseEntity.noContent().build();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity deletePid(@PathVariable("prefix") final String prefix,
            @PathVariable("suffix") final String suffix,
            final WebRequest request,
            final HttpServletResponse response) {
        return delete(prefix + "/" + suffix, request, response);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity createContent(@PathVariable(value = "id") final String identifier,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestPart(name = "metadata", required = false) final MultipartFile contentInformation,
            @RequestParam(name = "force", defaultValue = "false") boolean force,
            final WebRequest request,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        LOGGER.trace("Create content for resource with id '{}'. Force: '{}'", identifier, force);
        Function<String, String> createContent = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).createContent(t, file, contentInformation, force, request, response, uriBuilder)).toString();
        };
        DataResource resource = DataResourceUtils.getResourceByIdentifierOrRedirect(repositoryProperties, identifier, null, createContent);
        resource = writeLock.acquireEditable(resource.getId());
        String path = decodeContentPath(request);

        ContentInformation info = null;
        if (contentInformation != null) {
            LOGGER.trace("Reading user-provided content information.");
            try {
                info = new ObjectMapper().readValue(contentInformation.getInputStream(), ContentInformation.class);
                LOGGER.trace("Removing user-provided @Ids from content information.");
                EntityUtils.removeIds(info);
            } catch (IOException ex) {
                LOGGER.error("Unable to read content information metadata.", ex);
                return ResponseEntity.badRequest().body("Invalid ContentInformation metadata provided.");
            }
        }
        ContentInformation result = ContentDataUtils.addFile(repositoryProperties, resource, file, path, info, force, createContent);

        URI link = WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).getContentMetadata(resource.getId(), null, 1l, null, request, response, uriBuilder)).toUri();

        URIBuilder builder = new URIBuilder(link);
        builder.setPath(builder.getPath().replace("**", path));
        URI resourceUri = null;

        try {
            resourceUri = builder.build();
        } catch (URISyntaxException ex) {
            LOGGER.error("Failed to create location URI for path " + path + ". However, resource should be created.", ex);
            throw new CustomInternalServerError("Resource creation successful, but unable to create resource linkfor path " + path + ".");
        }

        indexResource(identifier, true);

        long currentVersion = contentAuditService.getCurrentVersion(Long.toString(result.getId()));
        if (currentVersion > 0) {
            return ResponseEntity.created(resourceUri).header(VERSION_HEADER, Long.toString(currentVersion)).eTag("\"" + result.getEtag() + "\"").build();
        } else {
            return ResponseEntity.created(resourceUri).eTag("\"" + result.getEtag() + "\"").build();
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity createContentPid(@PathVariable(value = "prefix") final String prefix,
            @PathVariable(value = "suffix") final String suffix,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestPart(name = "metadata", required = false) final MultipartFile contentInformation,
            @RequestParam(name = "force", defaultValue = "false") boolean force,
            final WebRequest request,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        return createContent(prefix + "/" + suffix, file, contentInformation, force, request, response, uriBuilder);
    }

    @Override
    public ResponseEntity getContentMetadata(@PathVariable(value = "id") final String identifier,
            @RequestParam(name = "tag", required = false) final String tag,
            @RequestParam(name = "version", required = false) final Long version,
            final Pageable pgbl,
            final WebRequest request,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        LOGGER.trace("Get content metadata for resource with id '{}' and version '{}'", identifier, version);

        Function<String, String> getContentMetadata = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).getContentMetadata(t, tag, version, pgbl, request, response, uriBuilder)).toString();
        };
        //check resource and permission
        DataResource resource = DataResourceUtils.getResourceByIdentifierOrRedirect(repositoryProperties, identifier, null, getContentMetadata);
        String path = decodeContentPath(request);

        List<ContentInformation> result = ContentDataUtils.readFiles(repositoryProperties, resource, path, tag, version, pgbl, getContentMetadata);

        if (path.endsWith("/") || path.length() == 0) {
            LOGGER.trace("Obtained {} content information result(s).", result.size());
            return ResponseEntity.ok().body(fixContentInformation(result, version));
        } else {
            LOGGER.trace("Obtained single content information result.");
            ContentInformation contentInformation = result.get(0);

            long currentVersion = contentAuditService.getCurrentVersion(Long.toString(contentInformation.getId()));
            if (currentVersion > 0) {
                return ResponseEntity.ok().eTag("\"" + contentInformation.getEtag() + "\"").header(VERSION_HEADER, Long.toString(currentVersion)).body(fixContentInformation(contentInformation, version));
            } else {
                return ResponseEntity.ok().eTag("\"" + contentInformation.getEtag() + "\"").body(fixContentInformation(contentInformation, version));
            }
        }

    }

    @Override
    public ResponseEntity getContentMetadataPid(@PathVariable(value = "prefix") final String prefix,
            @PathVariable(value = "suffix") final String suffix,
            @RequestParam(name = "tag", required = false) final String tag,
            @RequestParam(name = "version", required = false) final Long version,
            final Pageable pgbl,
            final WebRequest request,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        return getContentMetadata(prefix + "/" + suffix, tag, version, pgbl, request, response, uriBuilder);
    }

    @Override
    public ResponseEntity<List<ContentInformation>> findContentMetadataByExample(@RequestBody final ContentInformation example,
            final Pageable pgbl,
            final WebRequest wr,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {

        PageRequest request = ControllerUtils.checkPaginationInformation(pgbl);
        Page<ContentInformation> page = contentInformationService.findByExample(example, AuthenticationHelper.getAuthorizationIdentities(),
                AuthenticationHelper.hasAuthority(RepoUserRole.ADMINISTRATOR.toString()), pgbl);

        response.addHeader(CONTENT_RANGE_HEADER, ControllerUtils.getContentRangeHeader(page.getNumber(), request.getPageSize(), page.getTotalElements()));
        return ResponseEntity.ok().body(fixContentInformation(page.getContent(), null));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity patchContentMetadata(@PathVariable(value = "id") final String identifier,
            final @RequestBody JsonPatch patch,
            final WebRequest request,
            final HttpServletResponse response) {
        Function<String, String> patchContentMetadata = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).patchContentMetadata(t, patch, request, response)).toString();
        };
        String path = decodeContentPath(request);
        String eTag = ControllerUtils.getEtagFromHeader(request);
        if (repositoryProperties.isReadOnly()) throw new edu.kit.datamanager.exceptions.ServiceUnavailableException("Repository is in read-only mode.");
        DataResource editing = DataResourceUtils.getResourceByIdentifierOrRedirect(repositoryProperties, identifier, null, patchContentMetadata);
        writeLock.acquireEditable(editing.getId());
        ContentInformation toUpdate = ContentDataUtils.patchContentInformation(repositoryProperties, identifier, path, patch, eTag, patchContentMetadata);

        indexResource(identifier, true);

        long currentVersion = contentAuditService.getCurrentVersion(Long.toString(toUpdate.getId()));
        if (currentVersion > 0) {
            return ResponseEntity.noContent().header(VERSION_HEADER, Long.toString(currentVersion)).build();
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity patchContentMetadataPid(@PathVariable(value = "prefix") final String prefix,
            @PathVariable(value = "suffix") final String suffix,
            final @RequestBody JsonPatch patch,
            final WebRequest request,
            final HttpServletResponse response) {
        return patchContentMetadata(prefix + "/" + suffix, patch, request, response);
    }

    @Override
    public void getContent(@PathVariable(value = "id") final String identifier,
            @RequestParam(value = "version", required = false) Long version,
            final WebRequest request,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        LOGGER.trace("Get content for resource with id '{}' and version '{}'", identifier, version);
        String path = decodeContentPath(request);
        LOGGER.trace("Path: '{}'", path);
        String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);
        DataResource resource = DataResourceUtils.getResourceByIdentifierOrRedirect(repositoryProperties, identifier, null, (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).getContentMetadata(t, null, 1l, null, request, response, uriBuilder)).toString();
        });
        DataResourceUtils.performPermissionCheck(resource, PERMISSION.READ);
        LOGGER.debug("Access to resource with identifier {} granted. Continue with content access.", resource.getId());
        contentInformationService.read(resource, path, version, acceptHeader, response);
    }

    @Override
    public void getContentPid(@PathVariable(value = "prefix") final String prefix,
            @PathVariable(value = "suffix") final String suffix,
            @RequestParam(value = "version", required = false) Long version,
            final WebRequest request,
            final HttpServletResponse response,
            final UriComponentsBuilder uriBuilder) {
        getContent(prefix + "/" + suffix, version, request, response, uriBuilder);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity deleteContent(@PathVariable(value = "id")
            final String identifier,
            final WebRequest request,
            final HttpServletResponse response) {
        String path = decodeContentPath(request);
        String eTag = ControllerUtils.getEtagFromHeader(request);
        DataResource resource = null;
        ContentInformation prior = null;
        if (contentInformationDao != null && fileProvenance != null) {
            try {
                resource = DataResourceUtils.getResourceByIdentifierOrRedirect(repositoryProperties, identifier, null, value -> value);
                prior = contentInformationDao.findByParentResourceAndRelativePath(resource, path).orElse(null);
            } catch (RuntimeException ignored) {
                // Preserve the legacy deletion endpoint's own error semantics.
            }
        }
        Function<String, String> deleteContent = (t) -> {
            return WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).deleteContent(t, request, response)).toString();
        };
        if (repositoryProperties.isReadOnly()) throw new edu.kit.datamanager.exceptions.ServiceUnavailableException("Repository is in read-only mode.");
        DataResource deleting = DataResourceUtils.getResourceByIdentifierOrRedirect(repositoryProperties, identifier, null, deleteContent);
        writeLock.acquireEditable(deleting.getId());
        ContentDataUtils.deleteFile(repositoryProperties, identifier, path, eTag, deleteContent);

        if (prior != null && fileProvenance != null) {
            String actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() == null
                    ? "SYSTEM" : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            fileProvenance.save(new FileProvenanceEvent(resource.getId(), prior.getId(), path, "DELETED", actor,
                    prior.getMetadata() == null ? null : prior.getMetadata().get("sha256")));
            if (fileFixityStates != null && fileFixityStates.existsById(prior.getId())) fileFixityStates.deleteById(prior.getId());
        }

        indexResource(identifier, true);

        return ResponseEntity.noContent().build();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity deleteContentPid(@PathVariable(value = "prefix") final String prefix,
            @PathVariable(value = "suffix") final String suffix,
            final WebRequest request,
            final HttpServletResponse response) {
        return deleteContent(prefix + "/" + suffix, request, response);
    }

    private String decodeContentPath(WebRequest request) {
        return URLDecoder.decode(ContentDataUtils.getContentPathFromRequest(request), StandardCharsets.UTF_8);
    }

    private ContentInformation fixContentInformation(ContentInformation resource, Long version) {
        //hide all attributes but the id from the parent data resource in the content information entity
        String id = resource.getParentResource().getId();
        resource.setParentResource(DataResource.factoryNewDataResource(id));
        // fix content URI if URI points to a local file
        if (resource.getContentUri() != null && resource.getContentUri().startsWith("file:/")) {
            Long fileVersion = version != null ? version : 1l;
            String contentUri = WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(this.getClass()).getContentMetadata(id, null, fileVersion, null, null, null, null)).toString();
            contentUri = contentUri.replaceAll("\\*\\*", resource.getRelativePath());
            if ((version == null) || !applicationProperties.isAuditEnabled()) {
                // Remove path parameter version
                int qmIndex = contentUri.lastIndexOf("?");
                if (qmIndex > 0) {
                    contentUri = contentUri.substring(0, qmIndex);

                }
            }
            resource.setContentUri(contentUri);
        }
        return resource;
    }

    private List<ContentInformation> fixContentInformation(List<ContentInformation> resources, Long version) {
        //hide all attributes but the id from the parent data resource in all content information entities
        resources.forEach((resource) -> {
            fixContentInformation(resource, version);
        });
        return resources;
    }

    private void indexResource(String identifier, boolean includeContent) {
        if (dataResourceRepository.isPresent()) searchOutbox.enqueue(identifier);
    }

    private void unindexResource(String id) {
        if (dataResourceRepository.isPresent()) searchOutbox.enqueue(id);
    }
}
