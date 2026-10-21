package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.MessageTemplate;
import com.muhjain.school.model.enums.Gender;
import com.muhjain.school.model.enums.TemplateKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

	/** The 8 boarding SMS texts (one per event, per gender) shown on the Messages settings screen. Fee
	 * templates (FEE_RECEIPT, FEE_REMINDER, ...) share this table but have no gender, so they are excluded. */
	List<MessageTemplate> findAllByGenderIsNotNullOrderByCodeAscGenderAsc();

	/** A bus text: one code has a boy row and a girl row. */
	Optional<MessageTemplate> findByCodeAndGender(String code, Gender gender);

	/**
	 * Every row of this code. A bus code has two (one per gender), a fee code has one. Used only to find a
	 * provider template id at send time, when the exact gender used to build the message is not kept on the
	 * {@code message_outbox} row (a known gap until a future phase, see docs/08-decisions.md).
	 */
	List<MessageTemplate> findByCode(String code);

	/** The notification templates of one kind that are still in use (an edited APPROVED row is turned off). */
	List<MessageTemplate> findByKindAndActiveTrueOrderByNameAsc(TemplateKind kind);

	/** Every notification template still in use, for the Templates screen. SYSTEM texts are not in it. */
	List<MessageTemplate> findByKindNotAndActiveTrueOrderByNameAsc(TemplateKind kind);

}
