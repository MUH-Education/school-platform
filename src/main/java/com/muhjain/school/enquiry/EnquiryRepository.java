package com.muhjain.school.enquiry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

	/**
	 * The open enquiry (not ADMITTED, not LOST) with this phone and class, if any. The database allows only one
	 * (a unique index), so this is also what the 409 ENQUIRY_EXISTS points to.
	 */
	@Query("select e from Enquiry e where e.phone = :phone and e.classSought = :className "
			+ "and e.status not in (com.muhjain.school.enquiry.EnquiryStatus.ADMITTED, "
			+ "com.muhjain.school.enquiry.EnquiryStatus.LOST)")
	Optional<Enquiry> findOpen(@Param("phone") String phone, @Param("className") String className);

	/**
	 * The list, newest first. Every filter is optional (null or false = any). {@code village} is compared without
	 * regard to capital letters. {@code like} is a lower-case pattern like {@code %ramesh%} for the name, and
	 * {@code phoneLike} a pattern of digits for the phone. {@code overdue}: next follow-up before {@code today} and
	 * the enquiry is open.
	 */
	@Query("select e from Enquiry e where (:status is null or e.status = :status) "
			+ "and (:village is null or lower(e.village) = :village) and (:source is null or e.source = :source) "
			+ "and (:overdue = false or (e.nextFollowUpOn < :today and e.status not in "
			+ "(com.muhjain.school.enquiry.EnquiryStatus.ADMITTED, com.muhjain.school.enquiry.EnquiryStatus.LOST))) "
			+ "and (:like is null or lower(e.parentName) like :like or lower(e.childName) like :like "
			+ "or e.phone like :phoneLike) order by e.id desc")
	Page<Enquiry> search(@Param("status") EnquiryStatus status, @Param("village") String village,
			@Param("source") EnquirySource source, @Param("overdue") boolean overdue, @Param("today") LocalDate today,
			@Param("like") String like, @Param("phoneLike") String phoneLike, Pageable pageable);

	/** Each row is {status, count}. */
	@Query("select e.status, count(e) from Enquiry e group by e.status")
	List<Object[]> countByStatus();

	/** Each row is {village, count}, the biggest first. "Kanheri" and "kanheri" are one village. */
	@Query("select min(e.village), count(e) from Enquiry e group by lower(e.village) "
			+ "order by count(e) desc, min(e.village)")
	List<Object[]> countByVillage();

	@Query("select count(e) from Enquiry e where e.nextFollowUpOn < :today and e.status not in "
			+ "(com.muhjain.school.enquiry.EnquiryStatus.ADMITTED, com.muhjain.school.enquiry.EnquiryStatus.LOST)")
	long countOverdue(@Param("today") LocalDate today);

}
