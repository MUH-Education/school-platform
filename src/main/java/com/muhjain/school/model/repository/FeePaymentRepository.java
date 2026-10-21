package com.muhjain.school.model.repository;

import com.muhjain.school.model.entity.FeePayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FeePaymentRepository extends JpaRepository<FeePayment, Long> {

	List<FeePayment> findByReceiptIdOrderById(Long receiptId);

	List<FeePayment> findByStudentIdAndSessionIdOrderByMonthAscIdAsc(Long studentId, Long sessionId);

	boolean existsByStudentIdAndMonth(Long studentId, LocalDate month);

	/** Money received (lines plus their reversals) for one child and month, by head. */
	@Query("select p.feeHead, sum(p.amount) from FeePayment p where p.studentId = :studentId "
			+ "and p.month = :month group by p.feeHead")
	List<Object[]> paidByHead(@Param("studentId") Long studentId, @Param("month") LocalDate month);

	/** The lines whose receipt was paid (business date, {@code paidOn}) in the given range. Every filter is
	 * optional (null = any). */
	@Query("select p from FeePayment p where p.receiptId in (select r.id from Receipt r where "
			+ "r.paidOn >= :from and r.paidOn <= :to and (:sessionId is null or r.sessionId = :sessionId)) "
			+ "order by p.id desc")
	Page<FeePayment> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("sessionId") Long sessionId, Pageable pageable);

}
