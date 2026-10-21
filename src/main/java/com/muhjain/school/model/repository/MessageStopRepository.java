package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.MessageStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageStopRepository extends JpaRepository<MessageStop, Long> {

	Optional<MessageStop> findByGuardianId(Long guardianId);

	boolean existsByGuardianId(Long guardianId);

	/** Which of these families are on the stop list. One query for a whole audience. */
	List<MessageStop> findByGuardianIdIn(Collection<Long> guardianIds);

}
