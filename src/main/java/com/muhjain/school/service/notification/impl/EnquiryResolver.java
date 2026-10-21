package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.EnquiryContact;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.EnquiryStatus;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.service.enquiry.EnquiryService;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.util.RecipientText;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ENQUIRY_OPEN (NEW, CONTACTED, VISITED, APPLIED) and ENQUIRY_LOST: families who asked about admission. Only for
 * MARKETING and GREETING (the kind policies say so). Filter: classes (the class asked for).
 * Values: {@code Name}, {@code Parent name}, {@code Child name}, {@code Class}.
 */
@Component
public class EnquiryResolver implements RecipientResolver {

	private static final Set<EnquiryStatus> OPEN = EnumSet.of(EnquiryStatus.NEW, EnquiryStatus.CONTACTED,
			EnquiryStatus.VISITED, EnquiryStatus.APPLIED);

	private final EnquiryService enquiryService;

	public EnquiryResolver(EnquiryService enquiryService) {
		this.enquiryService = enquiryService;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.ENQUIRY_OPEN, AudienceGroup.ENQUIRY_LOST);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		Set<EnquiryStatus> statuses = (group == AudienceGroup.ENQUIRY_LOST) ? EnumSet.of(EnquiryStatus.LOST) : OPEN;
		return enquiryService.contacts(statuses)
			.stream()
			.filter(e -> filter.classes().isEmpty() || filter.classes().contains(e.classSought()))
			.map(e -> recipient(e, group))
			.toList();
	}

	private static Recipient recipient(EnquiryContact e, AudienceGroup group) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("Name", e.parentName());
		values.put("Parent name", e.parentName());
		values.put("Child name", RecipientText.firstName(e.childName()));
		values.put("Class", e.classSought());
		return new Recipient(RecipientType.ENQUIRY, e.id(), e.phone(), MessageChannel.WHATSAPP, group, List.of(),
				values);
	}

}
