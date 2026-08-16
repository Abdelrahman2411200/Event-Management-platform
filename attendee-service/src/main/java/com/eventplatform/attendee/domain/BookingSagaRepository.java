package com.eventplatform.attendee.domain;
import jakarta.persistence.LockModeType; import java.time.Instant; import java.util.*; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface BookingSagaRepository extends JpaRepository<BookingSaga,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from BookingSaga s where s.bookingId=:id") Optional<BookingSaga> findByIdForUpdate(@Param("id") UUID id);
 @Query("select s.bookingId from BookingSaga s where s.state in :states and s.nextActionAt<=:now order by s.nextActionAt") List<UUID> findDue(@Param("states") Collection<BookingSagaState> states,@Param("now") Instant now,Pageable pageable);
}
