package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.Staff;
import com.muhjain.school.model.enums.StaffType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

	/** The employees with this phone, turned on, oldest first (a phone is not unique among staff). */
	List<Staff> findByPhoneAndActiveTrueOrderByIdAsc(String phone);

}
