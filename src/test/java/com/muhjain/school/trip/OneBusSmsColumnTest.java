package com.muhjain.school.trip;

import com.muhjain.school.messaging.OutboxWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Task 5.9: "One bus" shows the SMS state per child and event. */
class OneBusSmsColumnTest extends TripTestBase {

	@Autowired
	private OutboxWorker worker;

	private void parent(long student, String phone) {
		long guardian = jdbc.queryForObject("insert into guardian (name, phone) values ('P', ?) returning id",
				Long.class, phone);
		jdbc.update("insert into student_guardian (student_id, guardian_id, relation) values (?, ?, 'OTHER')", student,
				guardian);
	}

	private String url() {
		return "/api/v1/bus-status/routes/" + route4 + "?phase=MORNING";
	}

	@Test
	void showsQueuedThenSentWithTimeAndNotForClassAndNone() throws Exception {
		parent(aryan, "+919811100001");
		jdbc.update("update student set class_name = '9' where id = ?", siya);
		parent(siya, "+919811100002");
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"), morning(siya, "DONE", "07:43:00"),
				morning(meera, "ABSENT", "07:44:00"));
		getAs(officeToken, url()).andExpect(jsonPath("$.children[?(@.name=='Aryan')].sms.boardedMorning.state")
			.value("QUEUED"));
		worker.runOnce();
		getAs(officeToken, url())
			// The log sender is the test provider, so the state is TEST_ONLY, with the time it was handled.
			.andExpect(jsonPath("$.children[?(@.name=='Aryan')].sms.boardedMorning.state").value("TEST_ONLY"))
			.andExpect(jsonPath("$.children[?(@.name=='Aryan')].sms.boardedMorning.sentAt").isNotEmpty())
			.andExpect(jsonPath("$.children[?(@.name=='Aryan')].sms.reachedSchool.state").value("NONE"))
			// Class 9 gets no morning boarding SMS.
			.andExpect(jsonPath("$.children[?(@.name=='Siya')].sms.boardedMorning.state").value("NOT_FOR_CLASS"))
			// Absent, no phone: nothing.
			.andExpect(jsonPath("$.children[?(@.name=='Meera')].sms.boardedMorning.state").value("NONE"));
	}

	@Test
	void failedSmsIsShownAsFailed() throws Exception {
		parent(aryan, "+919811100001");
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"));
		jdbc.update("update message_outbox set status = 'FAILED', error = 'invalid number'");
		getAs(officeToken, url()).andExpect(jsonPath("$.children[?(@.name=='Aryan')].sms.boardedMorning.state")
			.value("FAILED"));
	}

}
