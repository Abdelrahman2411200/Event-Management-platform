package com.eventplatform.auth.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, UUID> {

    Page<SecurityAuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
