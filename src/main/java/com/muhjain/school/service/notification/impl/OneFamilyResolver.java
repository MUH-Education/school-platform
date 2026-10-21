package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.FamilyContact;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.StudentBasics;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.model.enums.StudentStatus;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.service.student.StudentQueryService;
import com.muhjain.school.util.RecipientText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ONE_FAMILY: the phones picked by id ({@code familyIds} = guardian ids), with their ACTIVE children in
 * {@code Child name}. The office picks the exact phone, so it need not be the primary one. An unknown id is left
 * out. Example: familyIds [210] → Rajesh Punia, "Aryan और Siya".
 */
@Component
public class OneFamilyResolver implements RecipientResolver {

	private final GuardianService guardianService;

	private final StudentQueryService studentQuery;

	public OneFamilyResolver(GuardianService guardianService, StudentQueryService studentQuery) {
		this.guardianService = guardianService;
		this.studentQuery = studentQuery;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.ONE_FAMILY);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		List<Recipient> result = new ArrayList<>();
		for (Long guardianId : filter.familyIds().stream().distinct().toList()) {
			Optional<FamilyContact> contact = guardianService.contact(guardianId);
			if (contact.isEmpty()) {
				continue;
			}
			List<StudentBasics> children = studentQuery.basics(guardianService.childrenOf(guardianId))
				.values()
				.stream()
				.filter(c -> c.status() == StudentStatus.ACTIVE)
				.sorted(Comparator.comparing(StudentBasics::name))
				.toList();
			String parent = (contact.get().name() == null) ? "" : contact.get().name();
			Map<String, String> values = new LinkedHashMap<>();
			values.put("Name", parent);
			values.put("Parent name", parent);
			values.put("Child name", RecipientText.join(children.stream().map(c -> RecipientText.firstName(c.name())).toList()));
			values.put("Class", RecipientText.join(children.stream().map(StudentBasics::className).distinct().toList()));
			result.add(new Recipient(RecipientType.GUARDIAN, guardianId, contact.get().phone(), MessageChannel.WHATSAPP,
					group, children.stream().map(StudentBasics::id).toList(), values));
		}
		return result;
	}

}
