package com.muhjain.school.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.muhjain.school.model.entity.FeeDue;
import com.muhjain.school.model.enums.FeeHead;

public interface FeeDueRepository extends JpaRepository<FeeDue, Long> {

	List<FeeDue> findByFeePlanIdOrderByDueOnAscIdAsc(Long feePlanId);

	List<FeeDue> findByFeePlanIdIn(java.util.Collection<Long> feePlanIds);

	/** All dues of one fee head in a plan. Example: SCHOOL of a quarterly plan of 30000 → 30000.00. */
	@Query("select coalesce(sum(d.amount), 0) from FeeDue d where d.feePlanId = :planId and d.feeHead = :head")
	java.math.BigDecimal totalOf(@Param("planId") Long planId, @Param("head") FeeHead head);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from FeeDue d where d.feePlanId = :feePlanId")
	void deleteByFeePlanId(Long feePlanId);

}
