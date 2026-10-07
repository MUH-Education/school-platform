package com.muhjain.school.enquiry;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.common.PageResponse;
import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.student.ClassNames;
import com.muhjain.school.student.GuardianService;
import com.muhjain.school.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admission enquiries. Rules 1 to 7 of docs/phases/phase-6-enquiries.md.
 * Example: a parent calls for two minutes. The clerk saves 5 fields. The rest is added at the visit.
 */
@Service
public class EnquiryService {

	private final EnquiryRepository enquiries;

	private final EnquiryFollowUpRepository followUps;

	private final GuardianService guardianService;

	private final UserService userService;

	private final Clock clock;

	public EnquiryService(EnquiryRepository enquiries, EnquiryFollowUpRepository followUps,
			GuardianService guardianService, UserService userService, Clock clock) {
		this.userService = userService;
		this.enquiries = enquiries;
		this.followUps = followUps;
		this.guardianService = guardianService;
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
		return respond(save(enquiry));
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
		candidate.setSessionName(enquiry.getSessionName());
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
			.map(f -> new FollowUpResponse(f.getId(), f.getNote(), f.getNextActionOn(),
					f.getCreatedAt().atZone(clock.getZone()).toOffsetDateTime(), names.get(f.getCreatedBy())))
			.toList();
	}

	/**
	 * Moves an enquiry to another stage (rule 3, see {@link EnquiryTransitions}). LOST needs a reason. Opening a LOST
	 * enquiry again (to CONTACTED) clears the reason, and is refused when another open enquiry has the same phone and
	 * class.
	 *
	 * @throws ApiException 404 NOT_FOUND, 400 VALIDATION (no reason, or ADMITTED by hand), 409 STATUS_CHANGE_NOT_ALLOWED,
	 * 409 ENQUIRY_EXISTS
	 */
	@Transactional
	public EnquiryResponse changeStatus(Long id, StatusRequest request) {
		Enquiry enquiry = find(id);
		EnquiryStatus to = request.status();
		EnquiryTransitions.Move move = EnquiryTransitions.check(enquiry.getStatus(), to);
		switch (move) {
			case UNCHANGED -> {
				return respond(enquiry);
			}
			case ADMITTED_BY_HAND -> throw ApiException.validation("status",
					"ADMITTED is set only by an admission made from this enquiry");
			case NOT_ALLOWED -> throw new ApiException(HttpStatus.CONFLICT, "STATUS_CHANGE_NOT_ALLOWED",
					"An enquiry cannot go from " + enquiry.getStatus() + " to " + to + ".");
			case ALLOWED -> {
			}
		}
		String reason = (request.lostReason() == null) ? null : request.lostReason().strip();
		if (to == EnquiryStatus.LOST && (reason == null || reason.isEmpty())) {
			throw ApiException.validation("lostReason", "is needed when the enquiry is lost");
		}
		if (enquiry.getStatus() == EnquiryStatus.LOST) {
			// Opening again: no other open enquiry may have the same phone and class.
			enquiries.findOpen(enquiry.getPhone(), enquiry.getClassSought()).ifPresent(other -> {
				throw twin(other.getId());
			});
		}
		enquiry.setStatus(to);
		enquiry.setLostReason((to == EnquiryStatus.LOST) ? NameKeys.tidy(reason) : null);
		return respond(save(enquiry));
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
		if (request.nextActionOn() != null && request.nextActionOn().isBefore(today())) {
			throw ApiException.validation("nextActionOn", "cannot be in the past");
		}
		followUps.save(new EnquiryFollowUp(id, note, request.nextActionOn(), userId));
		if (enquiry.getStatus().isOpen()) {
			enquiry.setNextFollowUpOn(request.nextActionOn());
		}
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
		String session = blankToNull(request.sessionName());
		if (session != null && !session.matches("\\d{4}-\\d{2}")) {
			throw ApiException.validation("sessionName", "must look like 2027-28");
		}
		enquiry.setParentName(parentName);
		enquiry.setPhone(PhoneNumbers.normalize(request.phone()));
		enquiry.setRelation(blankToNull(request.relation()));
		enquiry.setVillage(village);
		enquiry.setChildName(blankToNull(request.childName()));
		enquiry.setClassSought(className);
		enquiry.setChildAge(blankToNull(request.childAge()));
		enquiry.setCurrentSchool(blankToNull(request.currentSchool()));
		enquiry.setSource(request.source());
		enquiry.setReferredBy(referredBy);
		enquiry.setReferredByGuardianId(request.referredByGuardianId());
		enquiry.setNeedsBus((request.needsBus() != null) ? request.needsBus() : NeedsBus.UNKNOWN);
		enquiry.setNextFollowUpOn(request.nextFollowUpOn());
		enquiry.setNote((request.note() == null || request.note().isBlank()) ? null : request.note().strip());
		enquiry.setSessionName((session != null) ? session
				: (enquiry.getSessionName() != null) ? enquiry.getSessionName() : SessionNames.nextSession(today()));
	}

	private static String blankToNull(String text) {
		return (text == null || text.isBlank()) ? null : NameKeys.tidy(text);
	}

}
