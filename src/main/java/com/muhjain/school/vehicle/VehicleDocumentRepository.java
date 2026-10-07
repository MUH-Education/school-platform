package com.muhjain.school.vehicle;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

	List<VehicleDocument> findByVehicleId(Long vehicleId);

	List<VehicleDocument> findByVehicleIdIn(Collection<Long> vehicleIds);

	/** Papers that end on or before a day. Used for the "needs attention" list. */
	List<VehicleDocument> findByValidTillLessThanEqualOrderByValidTillAscIdAsc(java.time.LocalDate day);

}
