package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

	// A removed stop is kept as active = false (old bus rows still point at it), so every normal
	// read excludes it. Only saveStops() looks at the full history, through findByRouteIdOrderBySeqNoAsc.
	List<RouteStop> findByRouteIdAndActiveTrueOrderBySeqNoAsc(Long routeId);

	List<RouteStop> findByRouteIdInAndActiveTrueOrderByRouteIdAscSeqNoAsc(Collection<Long> routeIds);

	List<RouteStop> findByRouteIdOrderBySeqNoAsc(Long routeId);

}
