package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.BusSmsRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusSmsRuleRepository extends JpaRepository<BusSmsRule, Long> {

	List<BusSmsRule> findAllByOrderBySeqNoAsc();

}
