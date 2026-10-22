package com.muhjain.school.service.notification.impl;

import com.muhjain.school.model.dto.response.AudienceFilter;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.service.notification.FamilyRecipients;
import com.muhjain.school.service.notification.RecipientResolver;
import com.muhjain.school.service.student.StudentQueryService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * BIRTHDAY_TODAY: the families of the ACTIVE children whose birthday is today (school time). Filter: classes. The
 * {@code Child name} is only the birthday child, not the brother or sister. Example: 23 Oct → Siya (born 23 Oct 2015)
 * → her family, {@code {Child name} = "Siya"}.
 */
@Component
public class BirthdayTodayResolver implements RecipientResolver {

	private final StudentQueryService studentQuery;

	private final FamilyRecipients families;

	private final Clock clock;

	public BirthdayTodayResolver(StudentQueryService studentQuery, FamilyRecipients families, Clock clock) {
		this.studentQuery = studentQuery;
		this.families = families;
		this.clock = clock;
	}

	@Override
	public Set<AudienceGroup> groups() {
		return Set.of(AudienceGroup.BIRTHDAY_TODAY);
	}

	@Override
	public List<Recipient> resolve(AudienceGroup group, AudienceFilter filter) {
		return families.of(studentQuery.activeWithBirthday(LocalDate.now(clock))
			.stream()
			.filter(c -> filter.classes().isEmpty() || filter.classes().contains(c.className()))
			.map(c -> new FamilyRecipients.Child(c.id(), c.name(), c.className()))
			.toList(), group);
	}

}
