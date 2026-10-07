package com.muhjain.school.fee;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassFeeRepository extends JpaRepository<ClassFee, Long> {

	List<ClassFee> findBySessionId(Long sessionId);

	Optional<ClassFee> findBySessionIdAndClassName(Long sessionId, String className);

}
