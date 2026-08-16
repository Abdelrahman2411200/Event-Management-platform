package com.eventplatform.event.outbox;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select message from OutboxMessage message
            where message.publishedAt is null
              and message.deadLetteredAt is null
              and message.nextAttemptAt <= :now
            order by message.occurredAt
            """)
    List<OutboxMessage> findPending(@Param("now") Instant now, Pageable pageable);
}
