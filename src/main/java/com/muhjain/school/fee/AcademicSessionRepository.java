package com.muhjain.school.fee;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {

	Optional<AcademicSession> findByCurrentTrue();

	boolean existsByNameIgnoreCase(String name);

	List<AcademicSession> findAllByOrderByStartsOnDesc();

}
