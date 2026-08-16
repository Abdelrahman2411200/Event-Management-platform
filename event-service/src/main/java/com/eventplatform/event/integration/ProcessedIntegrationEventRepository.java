package com.eventplatform.event.integration;
import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface ProcessedIntegrationEventRepository extends JpaRepository<ProcessedIntegrationEvent,UUID>{}
