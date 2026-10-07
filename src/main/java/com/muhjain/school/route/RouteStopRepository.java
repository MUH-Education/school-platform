package com.muhjain.school.route;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

	List<RouteStop> findByRouteIdOrderBySeqNoAsc(Long routeId);

	List<RouteStop> findByRouteIdInOrderByRouteIdAscSeqNoAsc(Collection<Long> routeIds);

}
