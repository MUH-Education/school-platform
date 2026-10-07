package com.muhjain.school.trip;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rules 1 to 10: who may tap, and how a tap is saved. */
class MarkApiTest extends TripTestBase {

	@MockitoSpyBean
	private BoardingNotifier notifier;

	@Test
	void sameTapSentTwiceIsSavedOnce() throws Exception {
		for (int i = 0; i < 3; i++) {
			sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10")).andExpect(status().isOk())
				.andExpect(jsonPath("$.results[0].ok").value(true))
				.andExpect(jsonPath("$.results[0].error").doesNotExist());
		}
		assertThat(rows()).isEqualTo(1);
		assertThat(outcomeOf(aryan, "BOARDED_MORNING")).isEqualTo("DONE");
		verify(notifier, times(1)).onDone(eq(aryan), eq(EventType.BOARDED_MORNING), any(), any());
	}

	@Test
	void olderTapDoesNotOverwriteNewerOne() throws Exception {
		sendMarks(balwanToken, morning(aryan, "ABSENT", "07:43:00")).andExpect(status().isOk());
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:00")).andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].ok").value(true));
		assertThat(outcomeOf(aryan, "BOARDED_MORNING")).isEqualTo("ABSENT");
		verify(notifier, never()).onDone(any(), any(), any(), any());
	}

	@Test
	void newerTapCorrectsTheRowAndNotifierRunsWhenItBecomesDone() throws Exception {
		sendMarks(balwanToken, morning(aryan, "ABSENT", "07:42:00"));
		sendMarks(balwanToken, morning(aryan, "DONE", "07:43:00"));
		assertThat(outcomeOf(aryan, "BOARDED_MORNING")).isEqualTo("DONE");
		assertThat(rows()).isEqualTo(1);
		verify(notifier, times(1)).onDone(eq(aryan), any(), any(), any());
	}

	@Test
	void notifierIsNotCalledForAbsent() throws Exception {
		sendMarks(balwanToken, morning(aryan, "ABSENT", "07:42:00"));
		verify(notifier, never()).onDone(any(), any(), any(), any());
	}

	@Test
	void clearedRemovesTheRow() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:00"));
		assertThat(rows()).isEqualTo(1);
		sendMarks(balwanToken, morning(aryan, "CLEARED", "07:44:00")).andExpect(jsonPath("$.results[0].ok").value(true));
		assertThat(rows()).isZero();
	}

	@Test
	void oneBadTapDoesNotStopTheOthers() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:00"), morning(dev, "DONE", "07:43:00"),
				morning(999999, "DONE", "07:43:00"), morning(siya, "ABSENT", "07:44:00"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.results.length()").value(4))
			.andExpect(jsonPath("$.results[0].ok").value(true))
			.andExpect(jsonPath("$.results[1].ok").value(false))
			.andExpect(jsonPath("$.results[1].error").value("NOT_YOUR_ROUTE"))
			.andExpect(jsonPath("$.results[2].error").value("UNKNOWN_STUDENT"))
			.andExpect(jsonPath("$.results[3].ok").value(true));
		assertThat(rows()).isEqualTo(2);
		assertThat(outcomeOf(dev, "BOARDED_MORNING")).isNull();
	}

	@Test
	void attendantCannotTapChildOfAnotherRoute() throws Exception {
		sendMarks(balwanToken, morning(dev, "DONE", "07:42:00")).andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].error").value("NOT_YOUR_ROUTE"));
		assertThat(rows()).isZero();
		verify(notifier, never()).onDone(any(), any(), any(), any());
	}

	@Test
	void eachAttendantCanTapOnlyOwnChildren() throws Exception {
		sendMarks(hariToken, morning(dev, "DONE", "07:42:00"), morning(aryan, "DONE", "07:42:00"))
			.andExpect(jsonPath("$.results[0].ok").value(true))
			.andExpect(jsonPath("$.results[1].error").value("NOT_YOUR_ROUTE"));
		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void attendantCannotWriteThreeDaysAgo() throws Exception {
		sendMarks(balwanToken, mark(aryan, "BOARDED_MORNING", "DONE", "2026-10-04", "2026-10-04T07:42:00+05:30"))
			.andExpect(jsonPath("$.results[0].error").value("DATE_NOT_ALLOWED"));
		assertThat(rows()).isZero();
	}

	@Test
	void attendantMayWriteYesterday() throws Exception {
		sendMarks(balwanToken, mark(aryan, "BOARDED_MORNING", "DONE", "2026-10-06", "2026-10-06T07:42:00+05:30"))
			.andExpect(jsonPath("$.results[0].ok").value(true));
		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void officeCanCorrectAnyRoute() throws Exception {
		sendMarks(officeToken, mark(dev, "BOARDED_MORNING", "DONE", "2026-10-04", "2026-10-04T07:42:00+05:30"))
			.andExpect(jsonPath("$.results[0].ok").value(true));
		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void childWithNoBusThatDayIsNotOnBus() throws Exception {
		// Meera's bus started on 1 Apr. On 31 Mar she was not on any bus.
		sendMarks(officeToken, mark(meera, "BOARDED_MORNING", "DONE", "2026-03-31", "2026-03-31T07:42:00+05:30"))
			.andExpect(jsonPath("$.results[0].error").value("NOT_ON_BUS"));
	}

	@Test
	void childWhoLeftIsUnknownStudent() throws Exception {
		jdbc.update("update student set status = 'LEFT', left_on = '2026-10-01' where id = ?", aryan);
		sendMarks(officeToken, morning(aryan, "DONE", "07:42:00"))
			.andExpect(jsonPath("$.results[0].error").value("UNKNOWN_STUDENT"));
	}

	@Test
	void notTravellingIsOnlyForEvening() throws Exception {
		sendMarks(balwanToken, morning(aryan, "NOT_TRAVELLING", "07:42:00"),
				mark(aryan, "BOARDED_EVENING", "NOT_TRAVELLING", TODAY, TODAY + "T07:45:00+05:30"))
			.andExpect(jsonPath("$.results[0].error").value("INVALID_OUTCOME"))
			.andExpect(jsonPath("$.results[1].ok").value(true));
		assertThat(rows()).isEqualTo(1);
		assertThat(outcomeOf(aryan, "BOARDED_EVENING")).isEqualTo("NOT_TRAVELLING");
	}

	@Test
	void futureTimeFromPhoneIsReplacedByServerTime() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "09:30:00")).andExpect(jsonPath("$.results[0].ok").value(true));
		Instant saved = jdbc.queryForObject("select occurred_at from boarding_event", Instant.class);
		assertThat(saved).isEqualTo(clock.instant()); // 7:48
		// 3 minutes ahead is only a small clock error, so it is kept.
		sendMarks(balwanToken, morning(siya, "DONE", "07:51:00"));
		assertThat(jdbc.queryForObject("select occurred_at from boarding_event where student_id = ?", Instant.class,
				siya)).isEqualTo(Instant.parse("2026-10-07T02:21:00Z"));
	}

	@Test
	void noTokenGives401AndWrongRoleGives403() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/trips/marks")
			.contentType("application/json").content("{\"marks\":[]}"))
			.andExpect(status().isUnauthorized());
		String desk = tokenFor(addUser("+919812340077", com.muhjain.school.user.Role.ADMISSIONS_DESK));
		sendMarks(desk, morning(aryan, "DONE", "07:42:00")).andExpect(status().isForbidden());
	}

}
