package com.muhjain.school.student;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * The WHERE part of the Students list (rules 22). Each filter is one condition. Phone and bus use sub-queries,
 * so the list has one row per student and no duplicates.
 */
final class StudentSpecs {

	private StudentSpecs() {
	}

	static Specification<Student> of(StudentFilter filter, LocalDate today) {
		return (root, query, cb) -> {
			List<Predicate> all = new ArrayList<>();
			all.add(cb.equal(root.get("status"), (filter.status() != null) ? filter.status() : StudentStatus.ACTIVE));
			if (filter.className() != null) {
				all.add(cb.equal(root.get("className"), filter.className()));
			}
			if (filter.village() != null && !filter.village().isBlank()) {
				all.add(cb.equal(cb.lower(root.get("village")), filter.village().strip().toLowerCase(Locale.ROOT)));
			}
			if (filter.q() != null && !filter.q().isBlank()) {
				all.add(search(filter.q().strip(), root, query, cb));
			}
			if (filter.routeId() != null) {
				all.add(cb.exists(busToday(query, cb, root, today, filter.routeId())));
			}
			if (filter.bus() == BusFilter.YES) {
				all.add(cb.exists(busToday(query, cb, root, today, null)));
			}
			else if (filter.bus() == BusFilter.NO) {
				all.add(cb.not(cb.exists(busToday(query, cb, root, today, null))));
			}
			return cb.and(all.toArray(new Predicate[0]));
		};
	}

	// q: part of the name, part of the admission number, or part of a phone number (digits only).
	private static Predicate search(String q, Root<Student> student, jakarta.persistence.criteria.CriteriaQuery<?> query,
			CriteriaBuilder cb) {
		String like = "%" + escape(q.toLowerCase(Locale.ROOT)) + "%";
		List<Predicate> any = new ArrayList<>();
		any.add(cb.like(cb.lower(student.get("name")), like, '\\'));
		any.add(cb.like(cb.lower(student.get("admissionNo")), like, '\\'));
		String digits = q.replaceAll("\\D", "");
		if (digits.length() >= 4) {
			Subquery<Long> sub = query.subquery(Long.class);
			Root<StudentGuardian> link = sub.from(StudentGuardian.class);
			Root<Guardian> guardian = sub.from(Guardian.class);
			sub.select(link.get("id"))
				.where(cb.equal(link.get("studentId"), student.get("id")),
						cb.equal(guardian.get("id"), link.get("guardianId")),
						cb.like(guardian.get("phone"), "%" + digits + "%"));
			any.add(cb.exists(sub));
		}
		return cb.or(any.toArray(new Predicate[0]));
	}

	// A bus row of the student that covers today, on this route if routeId is given.
	private static Subquery<Long> busToday(jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder cb,
			Root<Student> student, LocalDate today, Long routeId) {
		Subquery<Long> sub = query.subquery(Long.class);
		Root<TransportEnrolment> e = sub.from(TransportEnrolment.class);
		List<Predicate> where = new ArrayList<>();
		where.add(cb.equal(e.get("studentId"), student.get("id")));
		where.add(cb.lessThanOrEqualTo(e.<LocalDate>get("fromDate"), today));
		where.add(cb.or(cb.isNull(e.get("toDate")), cb.greaterThanOrEqualTo(e.<LocalDate>get("toDate"), today)));
		if (routeId != null) {
			where.add(cb.equal(e.get("routeId"), routeId));
		}
		return sub.select(e.<Long>get("id")).where(where.toArray(new Predicate[0]));
	}

	// A typed % or _ must not act as a wildcard.
	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
