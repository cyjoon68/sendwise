package com.sendwise.api.infra.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MessageEntity m join fetch m.campaign where m.id = :id")
    Optional<MessageEntity> lockById(long id);

    Page<MessageEntity> findByCampaign_Id(long campaignId, Pageable pageable);

    Page<MessageEntity> findByCampaign_IdAndStatus(long campaignId, String status, Pageable pageable);

    long countByFallbackOfId(long fallbackOfId);

    Optional<MessageEntity> findFirstByFallbackOfId(long fallbackOfId);

    boolean existsByIdAndStatus(long id, String status);

    List<MessageEntity> findByStatus(String status);

    @Query("select m.status, count(m) from MessageEntity m where m.campaign.id = :campaignId group by m.status")
    List<Object[]> countByStatus(long campaignId);

    @Query("select count(m) from MessageEntity m where m.campaign.id = :campaignId and m.fallbackOfId is not null")
    long countFallbacks(long campaignId);

    @Modifying
    @Query("update MessageEntity m set m.status = 'SENT' where m.id = :id and m.status = 'QUEUED'")
    int markSent(long id);

    @Query("""
            select m.carrier, count(m),
                   sum(case when m.status = 'FAILED' then 1 else 0 end)
            from MessageEntity m
            where m.createdAt >= :from and m.createdAt < :to
            group by m.carrier
            """)
    List<Object[]> statsByCarrier(Instant from, Instant to);

    @Query("""
            select coalesce(m.commonCode, 'PENDING'), count(m),
                   sum(case when m.status = 'FAILED' then 1 else 0 end)
            from MessageEntity m
            where m.createdAt >= :from and m.createdAt < :to
            group by coalesce(m.commonCode, 'PENDING')
            """)
    List<Object[]> statsByCode(Instant from, Instant to);
}
