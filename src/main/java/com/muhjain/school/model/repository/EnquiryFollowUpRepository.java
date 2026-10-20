package com.muhjain.school.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.EnquiryFollowUp;

public interface EnquiryFollowUpRepository extends JpaRepository<EnquiryFollowUp, Long> {

	/** Newest first. */
	List<EnquiryFollowUp> findByEnquiryIdOrderByIdDesc(Long enquiryId);

}
