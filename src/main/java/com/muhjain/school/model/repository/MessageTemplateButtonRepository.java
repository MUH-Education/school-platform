package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.MessageTemplateButton;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MessageTemplateButtonRepository extends JpaRepository<MessageTemplateButton, Long> {

	/** The buttons of one template, in the order WhatsApp shows them. */
	List<MessageTemplateButton> findByTemplateIdOrderBySeqNoAsc(Long templateId);

	/** Removes the buttons of a template before its new list is saved. */
	void deleteByTemplateId(Long templateId);

	/** The buttons of a page of templates, for the Templates list. */
	List<MessageTemplateButton> findByTemplateIdInOrderByTemplateIdAscSeqNoAsc(Collection<Long> templateIds);

}
