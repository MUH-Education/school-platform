package com.muhjain.school.student;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditChanges;
import com.muhjain.school.audit.AuditService;
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

	static final String ENTITY = "STUDENT";

	private final StudentRepository students;

	private final GuardianRepository guardians;

	private final StudentGuardianRepository links;

	private final AuditService auditService;

	public GuardianService(StudentRepository students, GuardianRepository guardians,
			StudentGuardianRepository links, AuditService auditService) {
		this.students = students;
		this.guardians = guardians;
		this.links = links;
		this.auditService = auditService;
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

	/**
	 * Rule 6 for the "add a phone" screen: {@link #linkPhone} plus a line in the change history.
	 * Example: "Phone +91XXXXXX0208 added (Grandfather)".
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION, 409 PHONE_ALREADY_LINKED
	 */
	@Transactional
	public GuardianResponse add(Long studentId, GuardianRequest request) {
		GuardianResponse saved = linkPhone(studentId, request.name(), request.phone(), request.relation(),
				request.sms());
		auditService.record(ENTITY, studentId, AuditAction.UPDATED,
				"Phone " + PhoneNumbers.mask(saved.phone()) + " added (" + saved.relation().label() + ")",
				Map.of("phone", PhoneNumbers.mask(saved.phone()), "relation", saved.relation().name()));
		return saved;
	}

	/**
	 * Rule 9: change name, relation or SMS of one phone of one child. The name belongs to the phone, so a new name
	 * shows for every child who uses the number. SMS on or off is for this child only.
	 * Example: grandfather's phone, SMS on → off for Siya. Aryan keeps SMS on.
	 *
	 * @throws ApiException 404 NOT_FOUND (student, or the phone is not saved for this child)
	 */
	@Transactional
	public GuardianResponse update(Long studentId, Long guardianId, UpdateGuardianRequest request) {
		StudentGuardian link = findLink(studentId, guardianId);
		Guardian guardian = guardians.findById(guardianId).orElseThrow();
		String masked = PhoneNumbers.mask(guardian.getPhone());
		String name = tidyName(request.name());
		AuditChanges changes = new AuditChanges()
			.field("Name of " + masked, "name", guardian.getName(), name)
			.field("Relation of " + masked, "relation", link.getRelation().label(), request.relation().label())
			.field("SMS for " + masked, "smsEnabled", onOff(link.isSmsEnabled()), onOff(request.smsEnabled()));
		guardian.setName(name);
		link.setRelation(request.relation());
		link.setSmsEnabled(request.smsEnabled());
		guardians.save(guardian);
		links.save(link);
		if (!changes.isEmpty()) {
			auditService.record(ENTITY, studentId, AuditAction.UPDATED, changes.summary(), changes.details());
		}
		return GuardianResponse.of(guardian, link);
	}

	/**
	 * Rules 7 and 8. Removes the link between this child and the phone. The last phone of a child cannot go.
	 * The guardian row stays, because another child may use it (and a later message log may point at it).
	 * If the removed phone was the primary one, the oldest remaining phone becomes primary.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 LAST_GUARDIAN
	 */
	@Transactional
	public void remove(Long studentId, Long guardianId) {
		StudentGuardian link = findLink(studentId, guardianId);
		List<StudentGuardian> all = links.findByStudentIdOrderByIdAsc(studentId);
		if (all.size() <= 1) {
			throw new ApiException(HttpStatus.CONFLICT, "LAST_GUARDIAN",
					"This is the last phone number of the child. Add another one first.");
		}
		Guardian guardian = guardians.findById(guardianId).orElseThrow();
		links.delete(link);
		if (link.isPrimary()) {
			all.stream().filter(other -> !other.getId().equals(link.getId())).findFirst().ifPresent(next -> {
				next.setPrimary(true);
				links.save(next);
			});
		}
		auditService.record(ENTITY, studentId, AuditAction.UPDATED,
				"Phone " + PhoneNumbers.mask(guardian.getPhone()) + " removed (" + link.getRelation().label() + ")",
				Map.of("phone", PhoneNumbers.mask(guardian.getPhone()), "relation", link.getRelation().name()));
	}

	private StudentGuardian findLink(Long studentId, Long guardianId) {
		if (!students.existsById(studentId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This student does not exist.");
		}
		return links.findByStudentIdAndGuardianId(studentId, guardianId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
					"This phone number is not saved for this child."));
	}

	private static String onOff(boolean on) {
		return on ? "on" : "off";
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

	/**
	 * The first (primary) phone of each child, for the Students list. A child with no phone is not in the map.
	 * Example: {118 → "+919812340208"}.
	 */
	@Transactional(readOnly = true)
	public Map<Long, String> firstPhones(java.util.Collection<Long> studentIds) {
		if (studentIds.isEmpty()) {
			return Map.of();
		}
		List<StudentGuardian> found = links.findByStudentIdInOrderByIdAsc(studentIds);
		Map<Long, String> phones = guardians.findByIdIn(found.stream().map(StudentGuardian::getGuardianId).toList())
			.stream()
			.collect(Collectors.toMap(Guardian::getId, Guardian::getPhone));
		Map<Long, String> first = new java.util.HashMap<>();
		Map<Long, Boolean> isPrimary = new java.util.HashMap<>();
		for (StudentGuardian link : found) {
			// The primary phone wins. If none is primary, the oldest link wins.
			if (!first.containsKey(link.getStudentId()) || (link.isPrimary() && !isPrimary.get(link.getStudentId()))) {
				first.put(link.getStudentId(), phones.get(link.getGuardianId()));
				isPrimary.put(link.getStudentId(), link.isPrimary());
			}
		}
		return first;
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

	/** Does this guardian (phone of a parent) exist? Enquiries use it to check {@code referredByGuardianId}. */
	@Transactional(readOnly = true)
	public boolean exists(Long guardianId) {
		return guardians.existsById(guardianId);
	}

}
