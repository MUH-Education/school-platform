package com.muhjain.school.fee;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long>, JpaSpecificationExecutor<FeePayment> {

	List<FeePayment> findByStudentIdAndSessionIdOrderByPaidOnAscIdAsc(Long studentId, Long sessionId);

	List<FeePayment> findByReceiptNoOrderById(String receiptNo);

	/** Money received for one head in a session. Correction rows (below 0) are counted too. */
	@Query("select coalesce(sum(p.amount), 0) from FeePayment p where p.studentId = :studentId "
			+ "and p.sessionId = :sessionId and p.feeHead = :head")
	java.math.BigDecimal paidFor(@Param("studentId") Long studentId, @Param("sessionId") Long sessionId,
			@Param("head") FeeHead head);

	/** The rows of one receipt for one head. Example: R-2026-0412, SCHOOL → the SCHOOL row and its corrections. */
	List<FeePayment> findByStudentIdAndReceiptNoAndFeeHead(Long studentId, String receiptNo, FeeHead head);

}
