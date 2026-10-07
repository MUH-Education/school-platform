package com.muhjain.school.enquiry;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * An enquiry as the screens see it. {@code overdue} is calculated (next follow-up before today, and the enquiry is not
 * ADMITTED or LOST); {@code overdueSince} is that date. {@code followUps} is filled only for one enquiry
 * ({@code GET /enquiries/{id}}), newest first, and is empty in the list.
 */
public record EnquiryResponse(Long id, String parentName, String phone, String relation, String village,
		String childName, String classSought, String childAge, String currentSchool, EnquirySource source,
		String referredBy, Long referredByGuardianId, NeedsBus needsBus, EnquiryStatus status, String lostReason,
		LocalDate nextFollowUpOn, boolean overdue, LocalDate overdueSince, String note, String sessionName,
		Long admittedStudentId, OffsetDateTime createdAt, List<FollowUpResponse> followUps) {

	static EnquiryResponse of(Enquiry e, LocalDate today, ZoneId zone, List<FollowUpResponse> followUps) {
		boolean overdue = isOverdue(e, today);
		return new EnquiryResponse(e.getId(), e.getParentName(), e.getPhone(), e.getRelation(), e.getVillage(),
				e.getChildName(), e.getClassSought(), e.getChildAge(), e.getCurrentSchool(), e.getSource(),
				e.getReferredBy(), e.getReferredByGuardianId(), e.getNeedsBus(), e.getStatus(), e.getLostReason(),
				e.getNextFollowUpOn(), overdue, overdue ? e.getNextFollowUpOn() : null, e.getNote(),
				e.getSessionName(), e.getAdmittedStudentId(), e.getCreatedAt().atZone(zone).toOffsetDateTime(),
				followUps);
	}

	/** Overdue: next follow-up before today, and the enquiry is still open. Example on 7 Oct: due 5 Oct, CONTACTED. */
	static boolean isOverdue(Enquiry e, LocalDate today) {
		return e.getNextFollowUpOn() != null && e.getNextFollowUpOn().isBefore(today) && e.getStatus().isOpen();
	}

}
