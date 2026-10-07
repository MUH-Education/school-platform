package com.muhjain.school.staff;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRepository extends JpaRepository<Staff, Long> {

	List<Staff> findAllByOrderByIdAsc();

	/** The person with a row lock until the transaction ends. Used when a person is put on a vehicle. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Staff s where s.id = :id")
	Optional<Staff> findByIdForUpdate(@Param("id") Long id);

}
