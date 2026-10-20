package com.muhjain.school.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findByEntityTypeAndEntityIdOrderByChangedAtDescIdDesc(String entityType, Long entityId);

}
