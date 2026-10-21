package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.ClassFee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassFeeRepository extends JpaRepository<ClassFee, Long> {

	List<ClassFee> findBySessionId(Long sessionId);

	Optional<ClassFee> findBySessionIdAndClassName(Long sessionId, String className);

}
