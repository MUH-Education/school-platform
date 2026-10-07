package com.muhjain.school.vehicle;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	List<Vehicle> findAllByOrderByIdAsc();

	List<Vehicle> findByActiveTrueOrderByIdAsc();

	/**
	 * The vehicle with a row lock until the transaction ends. Used when people are changed,
	 * so two changes on the same vehicle cannot run at the same moment.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from Vehicle v where v.id = :id")
	Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);

	/**
	 * Is there another vehicle with this name? {@code nameKey} is {@code NameKeys.key(name)}.
	 * Pass 0 as {@code exceptId} when adding a new vehicle.
	 */
	@Query("select count(v) > 0 from Vehicle v where upper(replace(v.name, ' ', '')) = :nameKey and v.id <> :exceptId")
	boolean existsByNameKey(@Param("nameKey") String nameKey, @Param("exceptId") Long exceptId);

	/** Same for the registration number. */
	@Query("select count(v) > 0 from Vehicle v where upper(replace(v.registrationNo, ' ', '')) = :registrationKey "
			+ "and v.id <> :exceptId")
	boolean existsByRegistrationKey(@Param("registrationKey") String registrationKey,
			@Param("exceptId") Long exceptId);

}
