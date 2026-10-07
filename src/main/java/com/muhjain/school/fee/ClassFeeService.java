package com.muhjain.school.fee;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import com.muhjain.school.student.ClassNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The standard school fee of each class (rule 2 of phase 7). The admission form reads it to fill the fee field.
 * Example: session 2026-27, class 3 → 30000.00.
 */
@Service
public class ClassFeeService {

	private final ClassFeeRepository classFees;

	private final SessionService sessions;

	private final AuditService audit;

	public ClassFeeService(ClassFeeRepository classFees, SessionService sessions, AuditService audit) {
		this.classFees = classFees;
		this.sessions = sessions;
		this.audit = audit;
	}

	/** In school order (Nursery first, class 12 last). */
	@Transactional(readOnly = true)
	public List<ClassFeeItem> list(Long sessionId) {
		sessions.get(sessionId);
		return classFees.findBySessionId(sessionId)
			.stream()
			.sorted(Comparator.comparingInt(f -> ClassNames.ALL.indexOf(f.getClassName())))
			.map(f -> new ClassFeeItem(f.getClassName(), f.getSchoolFee()))
			.toList();
	}

	/** The standard fee of one class in one session, if the office has set it. */
	@Transactional(readOnly = true)
	public Optional<BigDecimal> standardFee(Long sessionId, String className) {
		return classFees.findBySessionIdAndClassName(sessionId, className).map(ClassFee::getSchoolFee);
	}

	/** Saves the whole list. Classes that are not in it lose their standard fee. */
	@Transactional
	public List<ClassFeeItem> replace(Long sessionId, ClassFeesRequest request) {
		sessions.get(sessionId);
		Map<String, BigDecimal> wanted = new java.util.LinkedHashMap<>();
		List<String> problems = new ArrayList<>();
		for (ClassFeeItem item : request.fees()) {
			Optional<String> name = ClassNames.parse(item.className());
			if (name.isEmpty()) {
				throw ApiException.validation("fees", item.className() + " is not a class of this school");
			}
			if (wanted.put(name.get(), item.schoolFee().setScale(2)) != null) {
				throw ApiException.validation("fees", "class " + name.get() + " is in the list twice");
			}
		}
		List<ClassFee> existing = classFees.findBySessionId(sessionId);
		Map<String, ClassFee> byName = existing.stream().collect(Collectors.toMap(ClassFee::getClassName, f -> f));
		Set<String> changed = new HashSet<>();
		List<ClassFee> gone = existing.stream().filter(f -> !wanted.containsKey(f.getClassName())).toList();
		classFees.deleteAll(gone);
		classFees.flush();
		wanted.forEach((className, fee) -> {
			ClassFee row = byName.get(className);
			if (row == null) {
				classFees.save(new ClassFee(sessionId, className, fee));
				changed.add(className);
			}
			else if (row.getSchoolFee().compareTo(fee) != 0) {
				row.setSchoolFee(fee);
				changed.add(className);
			}
		});
		if (!changed.isEmpty() || !gone.isEmpty()) {
			audit.record("SESSION", sessionId, AuditAction.UPDATED,
					"Standard school fees changed for " + (changed.size() + gone.size()) + " class(es).", null);
		}
		return list(sessionId);
	}

}
