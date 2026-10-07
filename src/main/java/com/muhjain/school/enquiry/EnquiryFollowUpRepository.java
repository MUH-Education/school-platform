package com.muhjain.school.enquiry;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnquiryFollowUpRepository extends JpaRepository<EnquiryFollowUp, Long> {

	/** Newest first. */
	List<EnquiryFollowUp> findByEnquiryIdOrderByIdDesc(Long enquiryId);

}
