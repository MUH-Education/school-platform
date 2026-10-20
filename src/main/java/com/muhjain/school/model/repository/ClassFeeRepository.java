package com.muhjain.school.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.ClassFee;

public interface ClassFeeRepository extends JpaRepository<ClassFee, Long> {

	List<ClassFee> findBySessionId(Long sessionId);

	Optional<ClassFee> findBySessionIdAndClassName(Long sessionId, String className);

}
