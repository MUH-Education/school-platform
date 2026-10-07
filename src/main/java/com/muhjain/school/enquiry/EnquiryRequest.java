package com.muhjain.school.enquiry;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Add or change an enquiry. Five fields are needed: parent name, phone, village, class wanted, source. The clerk
 * types these in a two-minute call and adds the rest at the visit. {@code PUT} sends the whole object again.
 * <ul>
 * <li>{@code phone}: any way of typing it; it is saved as +91XXXXXXXXXX.</li>
 * <li>{@code classSought}: Nursery, LKG, UKG, 1 to 12.</li>
 * <li>{@code source} REFERRAL needs {@code referredBy}. {@code referredByGuardianId} is optional, the web app sends
 * it when the name or phone matched a known parent.</li>
 * <li>{@code needsBus}: default UNKNOWN. {@code sessionName}: "2027-28", default is the next admission session.</li>
 * <li>{@code nextFollowUpOn}: the first date to call back.</li>
 * </ul>
 * The stage is not here: use {@code POST /enquiries/{id}/status}.
 * Example: {@code {"parentName":"Ramesh Jain","phone":"98123 40208","village":"Jakhal","classSought":"3",
 * "source":"WALK_IN"}}.
 */
public record EnquiryRequest(@NotBlank @Size(max = 120) String parentName, @NotBlank String phone,
		@Size(max = 20) String relation, @NotBlank @Size(max = 80) String village,
		@Size(max = 120) String childName, @NotBlank String classSought, @Size(max = 20) String childAge,
		@Size(max = 120) String currentSchool, @NotNull EnquirySource source, @Size(max = 120) String referredBy,
		Long referredByGuardianId, NeedsBus needsBus, LocalDate nextFollowUpOn, @Size(max = 1000) String note,
		@Size(max = 9) String sessionName) {

}
