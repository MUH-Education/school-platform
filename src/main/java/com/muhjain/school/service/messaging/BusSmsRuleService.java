package com.muhjain.school.service.messaging;

import com.muhjain.school.model.entity.BusSmsRule;
import com.muhjain.school.model.enums.EventType;
import com.muhjain.school.model.repository.BusSmsRuleRepository;
import com.muhjain.school.util.ClassNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Which class gets which bus SMS. This is a promise the school makes to parents (docs/07-messaging.md),
 * read from {@code bus_sms_rule} (replaces the old hardcoded {@code SmsPolicy}).
 * Example: Class 3 → all four events. Class 9 → only REACHED_SCHOOL and BOARDED_EVENING. Class 11 → none.
 * A class that is in no rule gets nothing (no SMS is safer than a wrong SMS).
 */
@Service
public class BusSmsRuleService {

	private final BusSmsRuleRepository rules;

	public BusSmsRuleService(BusSmsRuleRepository rules) {
		this.rules = rules;
	}

	@Transactional(readOnly = true)
	public boolean allows(String className, EventType eventType) {
		if (className == null) {
			return false;
		}
		for (BusSmsRule rule : rules.findAllByOrderBySeqNoAsc()) {
			List<String> classes = ClassNames.expand(rule.getFromClass() + "-" + rule.getToClass()).orElse(List.of());
			if (!classes.contains(className)) {
				continue;
			}
			return switch (eventType) {
				case BOARDED_MORNING -> rule.isBoardedMorning();
				case REACHED_SCHOOL -> rule.isReachedSchool();
				case BOARDED_EVENING -> rule.isBoardedEvening();
				case REACHED_HOME -> rule.isReachedHome();
			};
		}
		return false;
	}

}
