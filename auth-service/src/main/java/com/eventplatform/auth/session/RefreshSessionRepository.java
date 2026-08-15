package com.eventplatform.auth.session;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshSession session where session.id = :id")
    Optional<RefreshSession> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshSession session
               set session.revokedAt = :now, session.revokeReason = :reason
             where session.familyId = :familyId and session.revokedAt is null
            """)
    int revokeActiveFamily(
            @Param("familyId") UUID familyId,
            @Param("reason") String reason,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshSession session
               set session.revokedAt = :now, session.revokeReason = :reason
             where session.userId = :userId and session.revokedAt is null
            """)
    int revokeActiveForUser(
            @Param("userId") UUID userId,
            @Param("reason") String reason,
            @Param("now") Instant now);
}
