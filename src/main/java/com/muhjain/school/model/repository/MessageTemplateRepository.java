package com.muhjain.school.model.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.muhjain.school.model.entity.MessageTemplate;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, String> {

	List<MessageTemplate> findAllByOrderByCodeAsc();

}
