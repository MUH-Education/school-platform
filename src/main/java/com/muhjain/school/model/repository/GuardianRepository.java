package com.muhjain.school.model.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.Guardian;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

	/** One phone = one row. {@code phone} is {@code +91XXXXXXXXXX}. */
	Optional<Guardian> findByPhone(String phone);

	List<Guardian> findByIdIn(Collection<Long> ids);

}
