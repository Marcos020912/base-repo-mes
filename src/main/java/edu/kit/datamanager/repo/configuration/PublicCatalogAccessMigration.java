package edu.kit.datamanager.repo.configuration;

import edu.kit.datamanager.entities.PERMISSION;
import edu.kit.datamanager.repo.dao.IDataResourceDao;
import edu.kit.datamanager.repo.domain.acl.AclEntry;
import edu.kit.datamanager.util.AuthenticationHelper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Makes catalogue records readable by every authenticated user, independently of their owner. */
@Configuration
public class PublicCatalogAccessMigration {
    @Bean CommandLineRunner makeCatalogReadable(IDataResourceDao resources, PlatformTransactionManager transactionManager) {
        return args -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            resources.findAll().forEach(resource -> {
                boolean alreadyReadable = resource.getAcls().stream().anyMatch(acl -> AuthenticationHelper.ANONYMOUS_USER_PRINCIPAL.equals(acl.getSid()) && acl.getPermission().compareTo(PERMISSION.READ) >= 0);
                if (!alreadyReadable) { resource.getAcls().add(new AclEntry(AuthenticationHelper.ANONYMOUS_USER_PRINCIPAL, PERMISSION.READ)); resources.save(resource); }
            })
        );
    }
}
