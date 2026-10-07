package com.muhjain.school.staff;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleAssignmentRepository extends JpaRepository<VehicleAssignment, Long> {

	/** Who worked on this vehicle, newest first. */
	List<VehicleAssignment> findByVehicleIdOrderByFromDateDescIdDesc(Long vehicleId);

	/**
	 * Rows of this vehicle that cover the day, oldest row first. Both kinds: permanent and temporary.
	 * Example: Van 4 on 14 Oct → Jagdish's permanent row and Surender's temporary row.
	 * The rule "temporary wins" is in {@link AssignmentRules}.
	 */
	@Query("select a from VehicleAssignment a where a.vehicleId = :vehicleId and a.fromDate <= :day "
			+ "and (a.toDate is null or a.toDate >= :day) order by a.id")
	List<VehicleAssignment> coveringDay(@Param("vehicleId") Long vehicleId, @Param("day") LocalDate day);

	/** Same for all vehicles at once. Used by the vehicle list and the staff list. */
	@Query("select a from VehicleAssignment a where a.fromDate <= :day and (a.toDate is null or a.toDate >= :day) "
			+ "order by a.id")
	List<VehicleAssignment> coveringDay(@Param("day") LocalDate day);

	/** Same for some vehicles. */
	@Query("select a from VehicleAssignment a where a.vehicleId in :vehicleIds and a.fromDate <= :day "
			+ "and (a.toDate is null or a.toDate >= :day) order by a.id")
	List<VehicleAssignment> coveringDay(@Param("vehicleIds") Collection<Long> vehicleIds,
			@Param("day") LocalDate day);

	/** Rows of one person that cover the day. */
	@Query("select a from VehicleAssignment a where a.staffId = :staffId and a.fromDate <= :day "
			+ "and (a.toDate is null or a.toDate >= :day) order by a.id")
	List<VehicleAssignment> ofStaffCoveringDay(@Param("staffId") Long staffId, @Param("day") LocalDate day);

	/** Rows of one person that touch any day between {@code from} and {@code to}. Used for STAFF_BUSY. */
	@Query("select a from VehicleAssignment a where a.staffId = :staffId and a.fromDate <= :to "
			+ "and (a.toDate is null or a.toDate >= :from) order by a.fromDate, a.id")
	List<VehicleAssignment> ofStaffBetween(@Param("staffId") Long staffId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	/** Permanent or temporary rows of one vehicle and duty that touch any day between {@code from} and {@code to}. */
	@Query("select a from VehicleAssignment a where a.vehicleId = :vehicleId and a.duty = :duty "
			+ "and a.temporary = :temporary and a.fromDate <= :to and (a.toDate is null or a.toDate >= :from) "
			+ "order by a.fromDate, a.id")
	List<VehicleAssignment> ofVehicleDutyBetween(@Param("vehicleId") Long vehicleId, @Param("duty") Duty duty,
			@Param("temporary") boolean temporary, @Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Permanent rows of one vehicle and duty, oldest first. */
	List<VehicleAssignment> findByVehicleIdAndDutyAndTemporaryFalseOrderByFromDateAscIdAsc(Long vehicleId, Duty duty);

	/** Is this person on a vehicle on this day or later? (rule 5) */
	@Query("select count(a) > 0 from VehicleAssignment a where a.staffId = :staffId "
			+ "and (a.toDate is null or a.toDate >= :day)")
	boolean existsOnOrAfter(@Param("staffId") Long staffId, @Param("day") LocalDate day);

	/** The first such row, to name the vehicle in the message. */
	@Query("select a from VehicleAssignment a where a.staffId = :staffId and (a.toDate is null or a.toDate >= :day) "
			+ "order by a.fromDate, a.id")
	List<VehicleAssignment> onOrAfter(@Param("staffId") Long staffId, @Param("day") LocalDate day);

	boolean existsByStaffId(Long staffId);

}
