package com.davidparry.agent.repository;

import com.davidparry.agent.entity.SecurityAuditLogEntity;
import com.davidparry.agent.entity.SecurityAuditLogEntity.EventCategory;
import com.davidparry.agent.entity.SecurityAuditLogEntity.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing security audit log data from the database.
 */
@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLogEntity, UUID> {

    /**
     * Find audit logs for a specific customer.
     */
    Page<SecurityAuditLogEntity> findByCustomerIdOrderByCreatedAtDesc(
        UUID customerId, Pageable pageable);

    /**
     * Find audit logs by event type within a time range.
     */
    List<SecurityAuditLogEntity> findByEventTypeAndCreatedAtBetween(
        EventType eventType, LocalDateTime start, LocalDateTime end);

    /**
     * Find audit logs by category within a time range.
     */
    List<SecurityAuditLogEntity> findByEventCategoryAndCreatedAtBetween(
        EventCategory category, LocalDateTime start, LocalDateTime end);

    /**
     * Find audit logs for a specific secret version.
     */
    List<SecurityAuditLogEntity> findBySecretVersionOrderByCreatedAtDesc(String secretVersion);

    /**
     * Count events by type for monitoring/metrics.
     */
    @Query("SELECT a.eventType, COUNT(a) FROM SecurityAuditLogEntity a "
           + "WHERE a.createdAt > :since GROUP BY a.eventType")
    List<Object[]> countEventsByTypeSince(LocalDateTime since);

    /**
     * Delete old audit logs (retention policy).
     */
    @Modifying
    int deleteByCreatedAtBefore(LocalDateTime cutoff);

    /**
     * Find audit logs with filters and pagination.
     * All filter parameters are optional (null = no filter).
     */
    @Query("SELECT a FROM SecurityAuditLogEntity a "
           + "WHERE (:customerId IS NULL OR a.customerId = :customerId) "
           + "AND (:eventType IS NULL OR a.eventType = :eventType) "
           + "AND (:eventCategory IS NULL OR a.eventCategory = :eventCategory) "
           + "AND (:from IS NULL OR a.createdAt >= :from) "
           + "AND (:to IS NULL OR a.createdAt <= :to) "
           + "ORDER BY a.createdAt DESC")
    Page<SecurityAuditLogEntity> findByFilters(
        UUID customerId,
        EventType eventType,
        EventCategory eventCategory,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable);
}
