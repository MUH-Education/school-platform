package com.muhjain.school.fee;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FeeDueRepository extends JpaRepository<FeeDue, Long> {

	List<FeeDue> findByFeePlanIdOrderByDueOnAscIdAsc(Long feePlanId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from FeeDue d where d.feePlanId = :feePlanId")
	void deleteByFeePlanId(Long feePlanId);

}
