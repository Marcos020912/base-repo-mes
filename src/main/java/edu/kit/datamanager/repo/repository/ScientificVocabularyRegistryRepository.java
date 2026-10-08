package edu.kit.datamanager.repo.repository;
import edu.kit.datamanager.repo.domain.ScientificVocabularyRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScientificVocabularyRegistryRepository extends JpaRepository<ScientificVocabularyRegistry,ScientificVocabularyRegistry.Kind>{}
