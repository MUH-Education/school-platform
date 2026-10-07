package com.muhjain.school.fee;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long>, JpaSpecificationExecutor<FeePayment> {

	List<FeePayment> findByStudentIdAndSessionIdOrderByPaidOnAscIdAsc(Long studentId, Long sessionId);

	List<FeePayment> findByReceiptNoOrderById(String receiptNo);

}
