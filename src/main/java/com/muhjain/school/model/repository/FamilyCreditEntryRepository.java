package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.FamilyCreditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface FamilyCreditEntryRepository extends JpaRepository<FamilyCreditEntry, Long> {

	/** The family's credit balance: every entry added up (kept money is +, used or refunded money is −). */
	@Query("select coalesce(sum(e.amount), 0) from FamilyCreditEntry e where e.guardianId = :guardianId")
	BigDecimal balanceOf(@Param("guardianId") Long guardianId);

}
