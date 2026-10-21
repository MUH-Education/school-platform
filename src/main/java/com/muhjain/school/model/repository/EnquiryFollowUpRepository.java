package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.EnquiryFollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnquiryFollowUpRepository extends JpaRepository<EnquiryFollowUp, Long> {

	/** Newest first. */
	List<EnquiryFollowUp> findByEnquiryIdOrderByIdDesc(Long enquiryId);

}
