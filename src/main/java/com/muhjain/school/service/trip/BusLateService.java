package com.muhjain.school.service.trip;

import com.muhjain.school.model.dto.response.RunningLateResponse;
import com.muhjain.school.model.enums.RuleEvent;
import com.muhjain.school.security.TripAccess;
import com.muhjain.school.service.notification.RuleEventListener;
import com.muhjain.school.service.notification.RuleEvents;
import com.muhjain.school.service.notification.RuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * The attendant's "running late" button (Phase 12, rule 4). It only fires the BUS_LATE event; the rule makes an
 * URGENT notification to the families on that route today, and Phase 11 sends it.
 * Example: Balwan (Route 4) taps "20 minutes" at 7:35 → the 19 Route 4 families get "Route 4 की बस 20 मिनट देर से है".
 * Once per route per morning (and per evening): a second tap the same morning sends nothing new.
 */
@Service
public class BusLateService {

	private final TripAccess tripAccess;

	private final RuleService ruleService;

	private final RuleEvents ruleEvents;

	private final Clock clock;

	public BusLateService(TripAccess tripAccess, RuleService ruleService, RuleEvents ruleEvents, Clock clock) {
		this.tripAccess = tripAccess;
		this.ruleService = ruleService;
		this.ruleEvents = ruleEvents;
		this.clock = clock;
	}

	/**
	 * @param userId the logged-in attendant, from the token
	 * @param routeId must be the attendant's own route today (never trusted from the phone)
	 * @throws com.muhjain.school.exception.ApiException 403 NOT_YOUR_ROUTE
	 */
	// A write: the event is handled only after this transaction commits.
	@Transactional
	public RunningLateResponse report(Long userId, Long routeId, int minutes) {
		LocalDate today = LocalDate.now(clock);
		tripAccess.forUser(userId).requireWrite(routeId, today);
		String runKey = RuleEventListener.busLateRunKey(routeId, ZonedDateTime.now(clock));
		boolean ruleOn = !ruleService.rulesFor(RuleEvent.BUS_LATE).isEmpty();
		boolean alreadySent = ruleOn && ruleService.alreadyRan(RuleEvent.BUS_LATE, runKey);
		if (ruleOn && !alreadySent) {
			ruleEvents.busLate(routeId, minutes);
		}
		return new RunningLateResponse(routeId, minutes, ruleOn && !alreadySent, alreadySent);
	}

}
