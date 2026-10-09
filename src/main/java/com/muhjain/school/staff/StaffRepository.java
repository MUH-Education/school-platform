package com.muhjain.school.staff;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRepository extends JpaRepository<Staff, Long> {

	List<Staff> findAllByOrderByIdAsc();

	/** Only some types. Example: DRIVER, ATTENDANT and HELPER for the Vehicles and staff screen. */
	List<Staff> findByStaffTypeInOrderByIdAsc(Collection<StaffType> types);

	/** Turned-on drivers whose licence ends on or before a day. Used for the "needs attention" list. */
	List<Staff> findByStaffTypeAndActiveTrueAndLicenceValidTillLessThanEqualOrderByLicenceValidTillAscIdAsc(
			StaffType staffType, LocalDate day);

	/** The person with a row lock until the transaction ends. Used when a person is put on a vehicle. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Staff s where s.id = :id")
	Optional<Staff> findByIdForUpdate(@Param("id") Long id);

}
