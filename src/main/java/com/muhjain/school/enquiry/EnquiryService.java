package com.muhjain.school.enquiry;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.student.ClassNames;
import com.muhjain.school.student.GuardianService;
import com.muhjain.school.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
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
