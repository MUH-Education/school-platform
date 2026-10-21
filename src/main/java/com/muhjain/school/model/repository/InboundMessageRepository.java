package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.InboundMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InboundMessageRepository extends JpaRepository<InboundMessage, Long> {

	/** The same webhook can arrive twice; the provider's id tells. */
	Optional<InboundMessage> findByProviderRef(String providerRef);

	/** The replies list, newest first: all, or only handled / not handled. */
	Page<InboundMessage> findAllByOrderByReceivedAtDescIdDesc(
			Pageable pageable);

	Page<InboundMessage> findByHandledOrderByReceivedAtDescIdDesc(boolean handled,
			Pageable pageable);

	/** Did this phone write to the school after this moment? (WhatsApp's 24-hour window.) */
	boolean existsByPhoneAndReceivedAtAfter(String phone, java.time.Instant after);

	/** Did this phone promise to pay by a day that has not passed? Example: promised until 23 Oct, today 21 Oct. */
	boolean existsByPhoneAndPromisedUntilGreaterThanEqual(String phone, java.time.LocalDate today);

}
