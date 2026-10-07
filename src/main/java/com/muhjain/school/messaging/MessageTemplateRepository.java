package com.muhjain.school.messaging;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, String> {

	List<MessageTemplate> findAllByOrderByCodeAsc();

}
