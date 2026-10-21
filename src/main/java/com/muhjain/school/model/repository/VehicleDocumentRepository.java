package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.VehicleDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

	List<VehicleDocument> findByVehicleId(Long vehicleId);

	List<VehicleDocument> findByVehicleIdIn(Collection<Long> vehicleIds);

	/** Papers that end on or before a day. Used for the "needs attention" list. */
	List<VehicleDocument> findByValidTillLessThanEqualOrderByValidTillAscIdAsc(java.time.LocalDate day);

}
