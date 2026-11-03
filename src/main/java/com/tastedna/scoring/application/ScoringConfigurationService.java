package com.tastedna.scoring.application;
import com.tastedna.scoring.persistence.ScoringConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ScoringConfigurationService {
    private final ScoringConfigurationRepository repository;
    private final JsonMapper mapper;
    public ScoringConfigurationService(ScoringConfigurationRepository repository, JsonMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }
    @Transactional(readOnly = true)
    public JsonNode load(String definitionId, String key) {
        var configuration = repository.findById(definitionId + ":" + key)
                .orElseThrow(() -> new IllegalStateException("SCORING_CONFIGURATION_NOT_FOUND"));
        if (!definitionId.equals(configuration.getDefinitionId()))
            throw new IllegalStateException("SCORING_CONFIGURATION_DEFINITION_MISMATCH");
        return mapper.readTree(configuration.getPayload());
    }
}

