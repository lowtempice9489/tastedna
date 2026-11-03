package com.tastedna.scoring.persistence;
import com.tastedna.scoring.domain.ScoringConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScoringConfigurationRepository extends JpaRepository<ScoringConfiguration, String> {}

