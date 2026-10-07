package com.muhjain.school.route;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRepository extends JpaRepository<Route, Long> {

	List<Route> findAllByOrderByIdAsc();

	List<Route> findByActiveTrueOrderByIdAsc();

	/** The active route of a vehicle. There is at most one (rule 13, and a unique index in the database). */
	Optional<Route> findByVehicleIdAndActiveTrue(Long vehicleId);

	List<Route> findByVehicleIdInAndActiveTrue(Collection<Long> vehicleIds);

	/** Is there another route with this name? {@code nameKey} is {@code NameKeys.key(name)}. Pass 0 for a new route. */
	@Query("select count(r) > 0 from Route r where upper(replace(r.name, ' ', '')) = :nameKey and r.id <> :exceptId")
	boolean existsByNameKey(@Param("nameKey") String nameKey, @Param("exceptId") Long exceptId);

}
