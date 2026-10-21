package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.EnquiryStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnquiryStageRepository extends JpaRepository<EnquiryStage, Long> {

	List<EnquiryStage> findByEnquiryIdOrderByReachedOnAscIdAsc(Long enquiryId);

}
