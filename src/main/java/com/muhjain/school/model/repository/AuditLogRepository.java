package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findByEntityTypeAndEntityIdOrderByChangedAtDescIdDesc(String entityType, Long entityId);

}
