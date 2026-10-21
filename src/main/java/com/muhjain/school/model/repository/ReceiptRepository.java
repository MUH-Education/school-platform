package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>, JpaSpecificationExecutor<Receipt> {

	Optional<Receipt> findByReceiptNo(String receiptNo);

	/** Did this family pay on this day? Example: guardian 210, PAYMENT, ACTIVE, 7 Oct 2026. */
	boolean existsByFamilyGuardianIdAndPaidOnAndKindAndStatus(Long familyGuardianId, java.time.LocalDate paidOn,
			com.muhjain.school.model.enums.ReceiptKind kind, com.muhjain.school.model.enums.ReceiptStatus status);

}
