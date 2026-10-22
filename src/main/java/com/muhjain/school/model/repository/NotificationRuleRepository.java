package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.NotificationRule;
import com.muhjain.school.model.enums.RuleEvent;
import com.muhjain.school.model.enums.RuleTrigger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

	/** The Settings table, in the order V15 seeded it. */
	List<NotificationRule> findAllByOrderByIdAsc();

	Optional<NotificationRule> findByCode(String code);

	/** The rules the scheduler looks at every minute. */
	List<NotificationRule> findByEnabledTrueAndTriggerTypeOrderByIdAsc(RuleTrigger triggerType);

	/** The rules an event fires. Example: BUS_LATE → the BUS_LATE rule, if it is on. */
	List<NotificationRule> findByEnabledTrueAndEventOrderByIdAsc(RuleEvent event);

	/** The rule, locked until the transaction ends, so an edit and a run never meet half way. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from NotificationRule r where r.id = :id")
	Optional<NotificationRule> findLockedById(@Param("id") Long id);

}
