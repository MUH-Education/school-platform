package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.FamilyContact;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.RecipientType;
import com.muhjain.school.service.student.GuardianService;
import com.muhjain.school.util.RecipientText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns a list of children into one recipient per family (rule 4). Used by every parent audience.
 * Example: Aryan (3) and Siya (11) share the phone of Rajesh Punia → one recipient with
 * {@code {Child name} = "Aryan और Siya"}, {@code {Class} = "3 और 11"}.
 * Values every family gets: {@code Name} and {@code Parent name} (the guardian's name, "" when unknown),
 * {@code Child name} (first names), {@code Class}. A child with no phone at all becomes its own recipient with no
 * phone, so it is counted as "no phone".
 */
@Component
public class FamilyRecipients {

	private final GuardianService guardianService;

	public FamilyRecipients(GuardianService guardianService) {
		this.guardianService = guardianService;
	}

	/** One child, as a family message needs it. Example: (118, "Aryan Punia", "3"). */
	public record Child(Long studentId, String name, String className) {
	}

	public List<Recipient> of(List<Child> children, AudienceGroup group) {
		return of(children, group, family -> Map.of());
	}

	/**
	 * @param extraValues more {box} values for a family, from its children. Example: the route name.
	 */
	public List<Recipient> of(List<Child> children, AudienceGroup group,
			Function<List<Child>, Map<String, String>> extraValues) {
		if (children.isEmpty()) {
			return List.of();
		}
		Map<Long, FamilyContact> contacts = guardianService.familyContacts(children.stream()
			.map(Child::studentId)
			.toList());
		// In the order of the children (the resolvers sort them by name), one entry per family.
		Map<Long, List<Child>> byFamily = new LinkedHashMap<>();
		List<Recipient> result = new ArrayList<>();
		for (Child child : children) {
			FamilyContact contact = contacts.get(child.studentId());
			if (contact == null) {
				result.add(recipient(null, List.of(child), group, extraValues));
				continue;
			}
			byFamily.computeIfAbsent(contact.guardianId(), id -> new ArrayList<>()).add(child);
		}
		byFamily.forEach((guardianId, family) -> result.add(recipient(contacts.get(family.getFirst().studentId()),
				family, group, extraValues)));
		return result;
	}

	private static Recipient recipient(FamilyContact contact, List<Child> family, AudienceGroup group,
			Function<List<Child>, Map<String, String>> extraValues) {
		String parent = (contact == null || contact.name() == null) ? "" : contact.name();
		Map<String, String> values = new LinkedHashMap<>();
		values.put("Name", parent);
		values.put("Parent name", parent);
		values.put("Child name", RecipientText.join(family.stream().map(c -> RecipientText.firstName(c.name())).toList()));
		values.put("Class", RecipientText.join(family.stream().map(Child::className).distinct().toList()));
		values.putAll(extraValues.apply(family));
		return new Recipient(RecipientType.GUARDIAN, (contact == null) ? null : contact.guardianId(),
				(contact == null) ? null : contact.phone(), MessageChannel.WHATSAPP, group,
				family.stream().map(Child::studentId).toList(), values);
	}

}
