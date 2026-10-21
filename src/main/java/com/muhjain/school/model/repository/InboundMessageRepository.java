package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.InboundMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InboundMessageRepository extends JpaRepository<InboundMessage, Long> {

	/** The same webhook can arrive twice; the provider's id tells. */
	Optional<InboundMessage> findByProviderRef(String providerRef);

}
