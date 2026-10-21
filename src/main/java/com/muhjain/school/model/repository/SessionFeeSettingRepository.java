package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.SessionFeeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionFeeSettingRepository extends JpaRepository<SessionFeeSetting, Long> {

	Optional<SessionFeeSetting> findBySessionId(Long sessionId);

}
