package com.muhjain.school.service.notification;

import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.AudienceCounts;
import com.muhjain.school.model.dto.response.Recipient;
import com.muhjain.school.model.dto.response.ResolvedAudience;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.MessageChannel;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.RecipientType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who gets a notification, right now (docs/10-notifications.md section 3):
 * union of the ticked groups → minus no phone → one per phone → minus the stop list (only when the kind's policy says
 * so). The numbers of each step are the counts of the "Who gets it" screen.
 * <p>
 * Example: MARKETING to CURRENT_PARENTS and STAFF_ALL. Balwan is an attendant and the father of a child here: found
 * in both groups, he gets it once (duplicates 1). The Malik family said stop: left out (stopped 1).
 * <p>
 * Nothing is stored: the dispatcher calls this again at send time, so a family that said stop at 6:29 pm is not in
 * the 6:30 pm list.
 */
@Service
public class AudienceService {

	private final Map<AudienceGroup, RecipientResolver> resolvers;

	private final KindPolicies policies;

	private final StopListService stopList;

	public AudienceService(List<RecipientResolver> resolvers, KindPolicies policies, StopListService stopList) {
		this.resolvers = byGroup(resolvers);
		this.policies = policies;
		this.stopList = stopList;
	}

	// Built from the Spring beans, never a switch. Every group has exactly one resolver, or the app does not start.
	static Map<AudienceGroup, RecipientResolver> byGroup(List<RecipientResolver> resolvers) {
		Map<AudienceGroup, RecipientResolver> map = new EnumMap<>(AudienceGroup.class);
		for (RecipientResolver resolver : resolvers) {
			for (AudienceGroup group : resolver.groups()) {
				RecipientResolver before = map.put(group, resolver);
				if (before != null) {
					throw new IllegalStateException("Two resolvers for " + group + ": "
							+ before.getClass().getSimpleName() + " and " + resolver.getClass().getSimpleName());
				}
			}
		}
		for (AudienceGroup group : AudienceGroup.values()) {
			if (!map.containsKey(group)) {
				throw new IllegalStateException("No RecipientResolver for " + group);
			}
		}
		return Collections.unmodifiableMap(map);
	}

	/**
	 * The people who get a notification of this kind with this audience, now. The kind and the audience are not
	 * checked here ({@link KindPolicies#checkAudience} does that when the notification is saved).
	 */
	@Transactional(readOnly = true)
	public ResolvedAudience resolve(NotificationKind kind, Audience audience) {
		KindPolicy policy = policies.forKind(kind);

		// 1. Union of the groups, in the order they were ticked.
		List<Recipient> picked = new ArrayList<>();
		for (AudienceGroup group : audience.groups().stream().distinct().toList()) {
			picked.addAll(resolvers.get(group).resolve(group, audience.filter()));
		}

		// 2. No phone: counted, never sent.
		List<Recipient> withPhone = picked.stream().filter(r -> r.phone() != null && !r.phone().isBlank()).toList();
		int noPhone = picked.size() - withPhone.size();

		// 3. One per phone. The first group wins: parents before staff when both are ticked in that order.
		Map<String, Recipient> byPhone = new LinkedHashMap<>();
		withPhone.forEach(r -> byPhone.putIfAbsent(r.phone(), r));
		int duplicates = withPhone.size() - byPhone.size();

		// 4. The stop list, only for MARKETING and GREETING. Staff are never touched by it.
		List<Recipient> recipients = new ArrayList<>();
		List<Recipient> stopped = new ArrayList<>();
		Set<String> stoppedPhones = policy.stopListApplies() ? stopList.stoppedPhones() : Set.of();
		for (Recipient r : byPhone.values()) {
			if (r.type() != RecipientType.STAFF && stoppedPhones.contains(r.phone())) {
				stopped.add(r);
			}
			else {
				recipients.add(r);
			}
		}

		int onWhatsApp = (int) recipients.stream().filter(r -> r.channel() == MessageChannel.WHATSAPP).count();
		int onSms = policy.alsoSmsWhenOnWhatsApp() ? recipients.size()
				: (int) recipients.stream().filter(r -> r.channel() == MessageChannel.SMS).count();
		AudienceCounts counts = new AudienceCounts(picked.size(), duplicates, stopped.size(), noPhone,
				recipients.size(), onWhatsApp, onSms);
		return new ResolvedAudience(List.copyOf(recipients), List.copyOf(stopped), counts);
	}

}
