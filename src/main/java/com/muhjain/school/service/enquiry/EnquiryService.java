package com.muhjain.school.service.enquiry;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.EnquiryRequest;
import com.muhjain.school.model.dto.request.FollowUpRequest;
import com.muhjain.school.model.dto.request.StatusRequest;
import com.muhjain.school.model.dto.response.*;
import com.muhjain.school.model.entity.Enquiry;
import com.muhjain.school.model.entity.EnquiryFollowUp;
import com.muhjain.school.model.entity.EnquiryStage;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.enums.EnquirySource;
import com.muhjain.school.model.enums.EnquiryStatus;
import com.muhjain.school.model.enums.NeedsBus;
import com.muhjain.school.model.repository.EnquiryFollowUpRepository;
import com.muhjain.school.model.repository.EnquiryRepository;
import com.muhjain.school.model.repository.EnquiryStageRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.service.fee.AcademicSessionService;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.service.user.UserService;
import com.muhjain.school.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Admission enquiries. Rules 1 to 7 of docs/phases/phase-6-enquiries.md.
 * Example: a parent calls for two minutes. The clerk saves 5 fields. The rest is added at the visit.
 */
@Service
public class EnquiryService {

	private final EnquiryRepository enquiries;

	private final EnquiryFollowUpRepository followUps;

	private final EnquiryStageRepository stages;

	private final GuardianService guardianService;

	private final UserService userService;

	private final AcademicSessionService sessionService;

	private final AuditService auditService;

	private final Clock clock;

	public EnquiryService(EnquiryRepository enquiries, EnquiryFollowUpRepository followUps,
			EnquiryStageRepository stages, GuardianService guardianService, UserService userService,
			AcademicSessionService sessionService, AuditService auditService, Clock clock) {
		this.userService = userService;
		this.auditService = auditService;
		this.enquiries = enquiries;
		this.followUps = followUps;
		this.stages = stages;
		this.guardianService = guardianService;
		this.sessionService = sessionService;
		this.clock = clock;
	}

	/**
	 * Saves a new enquiry with status NEW.
	 *
	 * @throws ApiException 400 VALIDATION, 409 ENQUIRY_EXISTS (an open enquiry with the same phone and class; the
	 * answer has {@code fields.enquiryId})
	 */
	@Transactional
	public EnquiryResponse create(EnquiryRequest request, Long userId) {
		Enquiry enquiry = new Enquiry(userId);
		apply(enquiry, request);
		checkNoOpenTwin(enquiry);
		Enquiry saved = save(enquiry);
		stages.save(new EnquiryStage(saved.getId(), EnquiryStatus.NEW, today(), userId));
		auditService.record(ENTITY, saved.getId(), AuditAction.CREATED,
				"Enquiry added: " + saved.getParentName() + ", " + saved.getVillage() + ", class "
						+ saved.getClassSought() + " (" + saved.getSource() + ")",
				details("source", saved.getSource().name(), "phone", PhoneNumbers.mask(saved.getPhone())));
		return respond(saved);
	}

	/**
	 * Changes the details (not the stage). A closed enquiry (ADMITTED, LOST) may be corrected too.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION, 409 ENQUIRY_EXISTS
	 */
	@Transactional
	public EnquiryResponse update(Long id, EnquiryRequest request) {
		Enquiry enquiry = find(id);
		// Check a copy first. Changing the saved object before the check would make the database complain (a unique
		// index) before we can answer with a clear 409.
		Enquiry candidate = new Enquiry(null);
		candidate.setSessionId(enquiry.getSessionId());
		candidate.setStatus(enquiry.getStatus());
		apply(candidate, request);
		if (candidate.getStatus().isOpen()) {
			enquiries.findOpen(candidate.getPhone(), candidate.getClassSought())
				.filter(other -> !other.getId().equals(id))
				.ifPresent(other -> {
					throw twin(other.getId());
				});
		}
		enquiry.copyDetailsFrom(candidate);
		return respond(save(enquiry));
	}

	/** @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public EnquiryResponse get(Long id) {
		Enquiry enquiry = find(id);
		return EnquiryResponse.of(enquiry, today(), clock.getZone(), followUpsOf(enquiry.getId()));
	}

	/** The calls and visits of one enquiry, newest first, with the name of who typed each. */
	List<FollowUpResponse> followUpsOf(Long enquiryId) {
		List<EnquiryFollowUp> rows = followUps.findByEnquiryIdOrderByIdDesc(enquiryId);
		Map<Long, String> names = userService.displayNames(rows.stream()
			.map(EnquiryFollowUp::getCreatedBy)
			.filter(java.util.Objects::nonNull)
			.collect(java.util.stream.Collectors.toSet()));
		return rows.stream()
			.map(f -> new FollowUpResponse(f.getId(), f.getHappenedOn(), f.getFollowType(), f.getNote(),
					f.getNextType(), f.getNextOn(), f.getCreatedAt().atZone(clock.getZone()).toOffsetDateTime(),
					(f.getCreatedBy() == null) ? null : names.get(f.getCreatedBy())))
			.toList();
	}

	/**
	 * Moves an enquiry to another stage (rule 3, see {@link EnquiryTransitions}). LOST needs a reason. Opening a
	 * LOST enquiry again goes back to exactly the stage it was lost from, and is refused when another open
	 * enquiry has the same phone and class.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION (no reason, or ADMITTED by hand), 409 STATUS_CHANGE_NOT_ALLOWED,
	 * 409 ENQUIRY_EXISTS
	 */
	@Transactional
	public EnquiryResponse changeStatus(Long id, StatusRequest request, Long userId) {
		Enquiry enquiry = find(id);
		EnquiryStatus from = enquiry.getStatus();
		EnquiryStatus to = request.status();
		EnquiryTransitions.Move move = EnquiryTransitions.check(from, to);
		switch (move) {
			case UNCHANGED -> {
				return respond(enquiry);
			}
			case ADMITTED_BY_HAND -> throw ApiException.validation("status",
					"ADMITTED is set only by an admission made from this enquiry");
			case NOT_ALLOWED -> throw new ApiException(HttpStatus.CONFLICT, "STATUS_CHANGE_NOT_ALLOWED",
					"An enquiry cannot go from " + from + " to " + to + ".");
			case ALLOWED -> {
			}
		}
		if (to == EnquiryStatus.LOST) {
			if (request.lostReason() == null) {
				throw ApiException.validation("lostReason", "is needed when the enquiry is lost");
			}
			enquiry.setStatusBeforeLost(from);
			enquiry.setLostReason(request.lostReason());
			enquiry.setLostNote(NameKeys.tidy(request.lostNote()));
		}
		else if (from == EnquiryStatus.LOST) {
			if (!to.equals(enquiry.getStatusBeforeLost())) {
				throw new ApiException(HttpStatus.CONFLICT, "STATUS_CHANGE_NOT_ALLOWED",
						"A lost enquiry can only be opened again to " + enquiry.getStatusBeforeLost() + ".");
			}
			// Opening again: no other open enquiry may have the same phone and class.
			enquiries.findOpen(enquiry.getPhone(), enquiry.getClassSought()).ifPresent(other -> {
				throw twin(other.getId());
			});
			enquiry.setStatusBeforeLost(null);
			enquiry.setLostReason(null);
			enquiry.setLostNote(null);
		}
		enquiry.setStatus(to);
		Enquiry saved = save(enquiry);
		stages.save(new EnquiryStage(id, to, today(), userId));
		String lostText = (saved.getLostNote() != null) ? saved.getLostNote()
				: (saved.getLostReason() != null) ? saved.getLostReason().name() : null;
		auditService.record(ENTITY, id, AuditAction.UPDATED,
				"Status changed from " + from + " to " + to + ((to == EnquiryStatus.LOST) ? ": " + lostText : ""),
				details("from", from.name(), "to", to.name()));
		return respond(saved);
	}

	/**
	 * Saves one call or visit (rule 4). The enquiry's next follow-up date is always the date of the newest follow-up:
	 * a follow-up with a date moves it, a follow-up with no date clears it (nothing is planned any more, so the
	 * enquiry is not overdue). A closed enquiry (ADMITTED, LOST) gets the note but keeps its date.
	 * Example: next date 5 Oct, the clerk calls on 7 Oct and sets 12 Oct → next date 12 Oct, no longer overdue.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION (note blank, date in the past)
	 */
	@Transactional
	public EnquiryResponse addFollowUp(Long id, FollowUpRequest request, Long userId) {
		Enquiry enquiry = find(id);
		String note = request.note().strip();
		if (note.isEmpty()) {
			throw ApiException.validation("note", "must not be blank");
		}
		if (request.nextOn() != null && request.nextOn().isBefore(today())) {
			throw ApiException.validation("nextOn", "cannot be in the past");
		}
		LocalDate happenedOn = (request.happenedOn() != null) ? request.happenedOn() : today();
		followUps.save(new EnquiryFollowUp(id, happenedOn, request.followType(), note, request.nextType(),
				request.nextOn(), userId));
		if (enquiry.getStatus().isOpen()) {
			enquiry.setNextFollowUpOn(request.nextOn());
			enquiry.setNextFollowUpType(request.nextType());
			save(enquiry);
		}
		auditService.record(ENTITY, id, AuditAction.UPDATED,
				"Follow-up added: " + shorten(note) + ((request.nextOn() != null)
						? ", next " + DayText.on(request.nextOn()) : ", no next date"),
				details("nextOn", (request.nextOn() != null) ? request.nextOn().toString() : null));
		return get(id);
	}

	/**
	 * The list, newest first, paged (rule 10). Filters are all optional.
	 *
	 * @param village any capital letters
	 * @param overdue true = only open enquiries whose next follow-up is before today (rule 5)
	 * @param q a part of the parent's or child's name, or at least 4 digits of the phone
	 * @throws ApiException 400 VALIDATION for a bad page or size
	 */
	@Transactional(readOnly = true)
	public PageResponse<EnquiryResponse> list(EnquiryStatus status, String village, EnquirySource source,
			boolean overdue, String q, int page, int size) {
		if (page < 0) {
			throw ApiException.validation("page", "must be 0 or more");
		}
		if (size < 1 || size > 100) {
			throw ApiException.validation("size", "must be between 1 and 100");
		}
		String villageKey = (village == null || village.isBlank()) ? null : NameKeys.tidy(village).toLowerCase(java.util.Locale.ROOT);
		String like = null;
		String phoneLike = null;
		if (q != null && !q.isBlank()) {
			String text = NameKeys.tidy(q).toLowerCase(java.util.Locale.ROOT);
			like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			String digits = text.replaceAll("\\D", "");
			if (digits.length() >= 4) {
				phoneLike = "%" + digits + "%";
			}
		}
		LocalDate today = today();
		return PageResponse.of(enquiries
			.search(status, villageKey, source, overdue, today, like, phoneLike, PageRequest.of(page, size))
			.map(e -> EnquiryResponse.of(e, today, clock.getZone(), List.of())));
	}

	/**
	 * Count per stage, total, overdue, admitted percent and count per village (rule 9).
	 * Example: 29 enquiries, 4 admitted → 14%.
	 */
	/**
	 * Every enquiry in these stages, as message recipients (notifications). Oldest first.
	 * Example: [NEW, CONTACTED, VISITED, APPLIED] → every open enquiry.
	 */
	@Transactional(readOnly = true)
	public List<EnquiryContact> contacts(java.util.Set<EnquiryStatus> statuses) {
		return enquiries.findByStatusInOrderByIdAsc(statuses)
			.stream()
			.map(e -> new EnquiryContact(e.getId(), e.getParentName(), e.getPhone(), e.getChildName(),
					e.getClassSought(), e.getStatus()))
			.toList();
	}

	/** The newest enquiry with this phone, if any. A WhatsApp reply from that phone is linked with it. */
	@Transactional(readOnly = true)
	public java.util.Optional<Long> latestIdByPhone(String phone) {
		return enquiries.findFirstByPhoneOrderByIdDesc(phone).map(Enquiry::getId);
	}

	/** The stage of one enquiry now, or empty if the id is unknown. Example: 77 → ADMITTED. */
	@Transactional(readOnly = true)
	public java.util.Optional<EnquiryStatus> statusOf(Long id) {
		return enquiries.findById(id).map(Enquiry::getStatus);
	}

	@Transactional(readOnly = true)
	public EnquirySummaryResponse summary() {
		Map<EnquiryStatus, Long> byStatus = new java.util.EnumMap<>(EnquiryStatus.class);
		for (EnquiryStatus status : EnquiryStatus.values()) {
			byStatus.put(status, 0L);
		}
		for (Object[] row : enquiries.countByStatus()) {
			byStatus.put((EnquiryStatus) row[0], ((Number) row[1]).longValue());
		}
		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		long admitted = byStatus.get(EnquiryStatus.ADMITTED);
		int percent = (total == 0) ? 0
				: java.math.BigDecimal.valueOf(admitted * 100L)
					.divide(java.math.BigDecimal.valueOf(total), 0, java.math.RoundingMode.HALF_UP)
					.intValue();
		List<EnquirySummaryResponse.VillageCount> villages = enquiries.countByVillage()
			.stream()
			.map(row -> new EnquirySummaryResponse.VillageCount((String) row[0], ((Number) row[1]).longValue()))
			.toList();
		return new EnquirySummaryResponse(total, byStatus, enquiries.countOverdue(today()), admitted, percent,
				villages);
	}

	/**
	 * Called by an admission that comes from an enquiry (rule 8), before the child is saved, so a wrong id stops the
	 * admission at once.
	 *
	 * @throws ApiException 400 VALIDATION (no such enquiry), 409 ENQUIRY_ALREADY_ADMITTED
	 */
	@Transactional(readOnly = true)
	public void requireAdmittable(Long enquiryId) {
		Enquiry enquiry = enquiries.findById(enquiryId)
			.orElseThrow(() -> ApiException.validation("enquiryId", "does not exist"));
		if (enquiry.getStatus() == EnquiryStatus.ADMITTED) {
			throw new ApiException(HttpStatus.CONFLICT, "ENQUIRY_ALREADY_ADMITTED",
					"A child was admitted from this enquiry already.");
		}
	}

	/**
	 * Closes the enquiry: ADMITTED, with the new student. The admission calls it in its own transaction (rule 8), so
	 * either both are saved or neither. This is the only way an enquiry becomes ADMITTED. An enquiry that was LOST
	 * may be admitted too (the parent came back).
	 */
	@Transactional
	public void markAdmitted(Long enquiryId, Long studentId, Long userId) {
		requireAdmittable(enquiryId);
		Enquiry enquiry = find(enquiryId);
		enquiry.setStatusBeforeLost(null);
		enquiry.setStatus(EnquiryStatus.ADMITTED);
		enquiry.setLostReason(null);
		enquiry.setLostNote(null);
		enquiry.setAdmittedStudentId(studentId);
		enquiries.saveAndFlush(enquiry);
		stages.save(new EnquiryStage(enquiryId, EnquiryStatus.ADMITTED, today(), userId));
		auditService.record(ENTITY, enquiryId, AuditAction.UPDATED, "Admitted: the child was admitted from this enquiry",
				details("studentId", String.valueOf(studentId)));
	}

	/**
	 * The fields the New admission screen can copy (task 6.9).
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 ENQUIRY_ALREADY_ADMITTED
	 */
	@Transactional(readOnly = true)
	public PrefillResponse prefill(Long id) {
		Enquiry e = find(id);
		if (e.getStatus() == EnquiryStatus.ADMITTED) {
			throw new ApiException(HttpStatus.CONFLICT, "ENQUIRY_ALREADY_ADMITTED",
					"A child was admitted from this enquiry already.");
		}
		LocalDate date = e.getCreatedAt().atZone(clock.getZone()).toLocalDate();
		return new PrefillResponse(e.getId(), e.getChildName(), e.getClassSought(), e.getVillage(), e.getNeedsBus(),
				new PrefillResponse.Guardian(e.getParentName(), e.getPhone(), relationOf(e.getRelation())),
				"Started from the enquiry of " + e.getParentName() + " (" + com.muhjain.school.util.DayText.on(date) + ")",
				date);
	}

	// The enquiry keeps the relation as free text. FATHER, mother, "Mother" all map. Anything else is OTHER.
	private static com.muhjain.school.model.enums.GuardianRelation relationOf(String text) {
		if (text != null) {
			String wanted = text.strip().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
			for (com.muhjain.school.model.enums.GuardianRelation relation : com.muhjain.school.model.enums.GuardianRelation.values()) {
				if (relation.name().equals(wanted)) {
					return relation;
				}
			}
		}
		return com.muhjain.school.model.enums.GuardianRelation.OTHER;
	}

	static final String ENTITY = "ENQUIRY";

	// A short line for the change history: the first 80 characters of a note.
	private static String shorten(String text) {
		return (text.length() <= 80) ? text : text.substring(0, 77) + "...";
	}

	private static Map<String, Object> details(String... keyValues) {
		Map<String, Object> details = new java.util.LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			details.put(keyValues[i], keyValues[i + 1]);
		}
		return details;
	}

	// ---- helpers shared with the other enquiry services in this package ----

	Enquiry find(Long id) {
		return enquiries.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This enquiry does not exist."));
	}

	EnquiryResponse respond(Enquiry enquiry) {
		return EnquiryResponse.of(enquiry, today(), clock.getZone(), List.of());
	}

	LocalDate today() {
		return LocalDate.now(clock);
	}

	/** Rule 6: only an open enquiry blocks. Two open enquiries of the same phone and class cannot exist. */
	void checkNoOpenTwin(Enquiry enquiry) {
		if (!enquiry.getStatus().isOpen()) {
			return;
		}
		enquiries.findOpen(enquiry.getPhone(), enquiry.getClassSought())
			.filter(other -> !other.getId().equals(enquiry.getId()))
			.ifPresent(other -> {
				throw twin(other.getId());
			});
	}

	Enquiry save(Enquiry enquiry) {
		try {
			return enquiries.saveAndFlush(enquiry);
		}
		catch (DataIntegrityViolationException ex) {
			// Two clerks saved the same call at the same moment: the unique index let one through.
			if (enquiry.getId() == null || enquiry.getStatus().isOpen()) {
				enquiries.findOpen(enquiry.getPhone(), enquiry.getClassSought())
					.filter(other -> !other.getId().equals(enquiry.getId()))
					.ifPresent(other -> {
						throw twin(other.getId());
					});
			}
			throw ex;
		}
	}

	private static ApiException twin(Long existingId) {
		return ApiException.conflict("ENQUIRY_EXISTS", "There is an open enquiry for this phone and class already. "
				+ "Open that one instead.", Map.of("enquiryId", String.valueOf(existingId)));
	}

	// Rules 1, 2 and 7: needed fields, class spelling, phone form, referral name.
	private void apply(Enquiry enquiry, EnquiryRequest request) {
		String className = ClassNames.parse(request.classSought())
			.orElseThrow(() -> ApiException.validation("classSought", "must be Nursery, LKG, UKG or 1 to 12"));
		String parentName = NameKeys.tidy(request.parentName());
		String village = NameKeys.tidy(request.village());
		if (parentName.isEmpty()) {
			throw ApiException.validation("parentName", "must not be blank");
		}
		if (village.isEmpty()) {
			throw ApiException.validation("village", "must not be blank");
		}
		String referredBy = blankToNull(request.referredBy());
		if (request.source() == EnquirySource.REFERRAL && referredBy == null) {
			throw ApiException.validation("referredBy", "is needed when the source is REFERRAL");
		}
		if (request.referredByGuardianId() != null && !guardianService.exists(request.referredByGuardianId())) {
			throw ApiException.validation("referredByGuardianId", "does not exist");
		}
		Long sessionId = request.sessionId();
		if (sessionId == null) {
			sessionId = sessionService.nextOrCurrent().getId();
		}
		else if (!sessionService.exists(sessionId)) {
			throw ApiException.validation("sessionId", "does not exist");
		}
		enquiry.setParentName(parentName);
		enquiry.setPhone(PhoneNumbers.normalize(request.phone()));
		// The database keeps a closed list (enquiry_relation_check): "mother", "Dadi" etc. become one
		// of FATHER/MOTHER/GRANDFATHER/GRANDMOTHER/UNCLE_AUNT/OTHER before the row is ever written.
		String relation = blankToNull(request.relation());
		enquiry.setRelation(relation == null ? null : relationOf(relation).name());
		enquiry.setVillage(village);
		enquiry.setChildName(blankToNull(request.childName()));
		enquiry.setClassSought(className);
		enquiry.setChildAge(blankToNull(request.childAge()));
		enquiry.setCurrentSchool(blankToNull(request.currentSchool()));
		enquiry.setSource(request.source());
		enquiry.setReferredBy(referredBy);
		enquiry.setReferredByGuardianId(request.referredByGuardianId());
		enquiry.setNeedsBus((request.needsBus() != null) ? request.needsBus() : NeedsBus.UNKNOWN);
		enquiry.setNeedsBusFrom(blankToNull(request.needsBusFrom()));
		enquiry.setNextFollowUpOn(request.nextFollowUpOn());
		enquiry.setNextFollowUpType(request.nextFollowUpType());
		enquiry.setNote((request.note() == null || request.note().isBlank()) ? null : request.note().strip());
		enquiry.setSessionId(sessionId);
	}

	private static String blankToNull(String text) {
		return (text == null || text.isBlank()) ? null : NameKeys.tidy(text);
	}

}
