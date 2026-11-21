package com.demo.insightservice.repository;

import com.demo.insightservice.entity.BreachedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BreachedUserRepository extends JpaRepository<BreachedUser, UUID> {

    @Query("select b from BreachedUser b where b.insightSentAt is null")
    List<BreachedUser> findUnsent();

    @Modifying
    @Query("update BreachedUser b set b.insightSentAt = :sentAt where b.userId = :userId and b.insightSentAt is null")
    int markUserSent(@Param("userId") UUID userId, @Param("sentAt") Instant sentAt);

    @Modifying
    @Query("delete from BreachedUser b where b.breachedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
