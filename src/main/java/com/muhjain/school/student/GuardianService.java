package com.muhjain.school.student;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.common.PhoneNumbers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The parents' phone numbers of a child. Rules 5 to 9 of docs/phases/phase-3-students-admission.md.
 * <p>
 * One phone number is one {@link Guardian} row, even if it belongs to several children.
 * Example: Siya is a student and her mother's phone is saved. Aryan is admitted with the same phone.
 * Result: still one guardian row, now with two {@link StudentGuardian} links.
 */
@Service
public class GuardianService {

	private final StudentRepository students;

	private final GuardianRepository guardians;

	private final StudentGuardianRepository links;

	public GuardianService(StudentRepository students, GuardianRepository guardians,
			StudentGuardianRepository links) {
		this.students = students;
		this.guardians = guardians;
		this.links = links;
	}

	/**
	 * Rules 5 and 6. A phone that exists already is reused. The first phone of a child becomes its primary phone.
	 * No audit row is written here, so the admission can add several phones and write one line for itself.
	 *
	 * @param name optional. It is saved only if the guardian has no name yet.
	 * @throws ApiException 404 NOT_FOUND (student), 400 VALIDATION (bad phone), 409 PHONE_ALREADY_LINKED
	 */
	@Transactional
	public GuardianResponse linkPhone(Long studentId, String name, String phone, GuardianRelation relation,
			boolean smsEnabled) {
		if (!students.existsById(studentId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist.");
		}
		String normalized = PhoneNumbers.normalize(phone);
		String tidyName = tidyName(name);
		Guardian guardian = guardians.findByPhone(normalized).orElseGet(() -> create(tidyName, normalized));
		if (guardian.getName() == null && tidyName != null) {
			guardian.setName(tidyName);
		}
		if (links.findByStudentIdAndGuardianId(studentId, guardian.getId()).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "PHONE_ALREADY_LINKED",
					"This phone number is already saved for this child.");
		}
		boolean first = links.findByStudentIdOrderByIdAsc(studentId).isEmpty();
		StudentGuardian link = links.save(new StudentGuardian(studentId, guardian.getId(), relation, smsEnabled, first));
		return GuardianResponse.of(guardian, link);
	}

	/** The phones of one child, oldest link first. The primary phone is the one with {@code primary = true}. */
	@Transactional(readOnly = true)
	public List<GuardianResponse> list(Long studentId) {
		List<StudentGuardian> found = links.findByStudentIdOrderByIdAsc(studentId);
		Map<Long, Guardian> byId = guardians.findByIdIn(found.stream().map(StudentGuardian::getGuardianId).toList())
			.stream()
			.collect(Collectors.toMap(Guardian::getId, g -> g));
		return found.stream().map(link -> GuardianResponse.of(byId.get(link.getGuardianId()), link)).toList();
	}

	static String tidyName(String name) {
		String tidy = NameKeys.tidy(name);
		return (tidy == null || tidy.isEmpty()) ? null : tidy;
	}

	// Two clerks may add the same new number at the same moment. The unique index stops the second one.
	private Guardian create(String name, String phone) {
		try {
			return guardians.saveAndFlush(new Guardian(name, phone));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "TRY_AGAIN",
					"Someone saved this phone number at the same moment. Please try again.");
		}
	}

}
