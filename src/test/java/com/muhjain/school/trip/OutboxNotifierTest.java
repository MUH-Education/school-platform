package com.muhjain.school.trip;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Rules 2 to 7 of Phase 5: the tap writes the parent SMS into the queue, in the same transaction.
 * Story: Aryan (class 3, boy) has two parent phones. Siya (class 3, girl) shares the mother's phone.
 */
class OutboxNotifierTest extends TripTestBase {

	private static final String FATHER = "+919811100001";

	private static final String MOTHER = "+919811100002";

	@Autowired
	private MarkService markService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private void parent(long student, String phone, boolean smsOn) {
		long guardian = jdbc.query("select id from guardian where phone = ?", rs -> rs.next() ? rs.getLong(1) : -1L,
				phone);
		if (guardian < 0) {
			guardian = jdbc.queryForObject("insert into guardian (name, phone) values ('Parent', ?) returning id",
					Long.class, phone);
		}
		jdbc.update("insert into student_guardian (student_id, guardian_id, relation, sms_enabled) values (?, ?, "
				+ "'OTHER', ?)", student, guardian, smsOn);
	}

	private int queued() {
		return jdbc.queryForObject("select count(*) from message_outbox", Integer.class);
	}

	private List<String> bodies() {
		return jdbc.queryForList("select body from message_outbox order by id", String.class);
	}

	private void setClass(String className) {
		jdbc.update("update student set class_name = ?", className);
	}

	@Test
	void tapCreatesOneOutboxRowPerParentPhone() throws Exception {
		parent(aryan, FATHER, true);
		parent(aryan, MOTHER, true);
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"));
		assertThat(queued()).isEqualTo(2);
		assertThat(jdbc.queryForList("select distinct status from message_outbox", String.class))
			.containsExactly("QUEUED");
		assertThat(bodies()).containsOnly("Aryan सुबह की बस में चढ़ गया — 7:42। MUH Jain School");
		assertThat(jdbc.queryForObject("select template_code from message_outbox limit 1", String.class))
			.isEqualTo("BOARDED_MORNING_M");
	}

	@Test
	void girlGetsTheGirlText() throws Exception {
		parent(siya, MOTHER, true);
		sendMarks(balwanToken, morning(siya, "DONE", "07:43:00"));
		assertThat(bodies()).containsExactly("Siya सुबह की बस में चढ़ गई — 7:43। MUH Jain School");
	}

	@Test
	void sharedPhoneGetsOneSmsPerChild() throws Exception {
		parent(aryan, MOTHER, true);
		parent(siya, MOTHER, true);
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:00"), morning(siya, "DONE", "07:43:00"));
		assertThat(queued()).isEqualTo(2);
	}

	@Test
	void class11TapCreatesNoRow() throws Exception {
		parent(aryan, FATHER, true);
		setClass("11");
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"),
				mark(aryan, "REACHED_SCHOOL", "DONE", TODAY, TODAY + "T08:05:00+05:30"));
		assertThat(queued()).isZero();
		assertThat(rows()).isEqualTo(2);
	}

	@Test
	void class9MorningBoardingCreatesNoRowButReachedSchoolDoes() throws Exception {
		parent(aryan, FATHER, true);
		setClass("9");
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"));
		assertThat(queued()).isZero();
		sendMarks(balwanToken, mark(aryan, "REACHED_SCHOOL", "DONE", TODAY, TODAY + "T08:05:00+05:30"));
		assertThat(queued()).isEqualTo(1);
		assertThat(bodies().get(0)).startsWith("Aryan स्कूल पहुँच गया");
	}

	@Test
	void absentCreatesNoRow() throws Exception {
		parent(aryan, FATHER, true);
		sendMarks(balwanToken, morning(aryan, "ABSENT", "07:42:10"));
		sendMarks(balwanToken, mark(aryan, "BOARDED_EVENING", "NOT_TRAVELLING", TODAY, TODAY + "T07:44:00+05:30"));
		assertThat(queued()).isZero();
	}

	@Test
	void yesterdaysLateTapCreatesNoRow() throws Exception {
		parent(aryan, FATHER, true);
		sendMarks(balwanToken, mark(aryan, "BOARDED_MORNING", "DONE", "2026-10-06", "2026-10-06T07:42:00+05:30"))
			.andExpect(jsonPath("$.results[0].ok").value(true));
		assertThat(rows()).isEqualTo(1);
		assertThat(queued()).isZero();
	}

	@Test
	void sameTapThreeTimesCreatesOneRow() throws Exception {
		parent(aryan, FATHER, true);
		for (int i = 0; i < 3; i++) {
			sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"));
		}
		assertThat(queued()).isEqualTo(1);
	}

	@Test
	void undoThenTapAgainDoesNotSendASecondSms() throws Exception {
		parent(aryan, FATHER, true);
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:00"));
		sendMarks(balwanToken, morning(aryan, "CLEARED", "07:43:00"));
		sendMarks(balwanToken, morning(aryan, "DONE", "07:44:00"));
		assertThat(rows()).isEqualTo(1);
		assertThat(queued()).isEqualTo(1);
		// The text keeps the time of the first tap.
		assertThat(bodies().get(0)).contains("7:42");
	}

	@Test
	void parentWithSmsOffGetsNothing() throws Exception {
		parent(aryan, FATHER, false);
		parent(aryan, MOTHER, true);
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"));
		assertThat(jdbc.queryForList("select phone from message_outbox", String.class)).containsExactly(MOTHER);
	}

	@Test
	void childWithNoPhoneCreatesNoRowAndTheTapIsSaved() throws Exception {
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10")).andExpect(jsonPath("$.results[0].ok").value(true));
		assertThat(rows()).isEqualTo(1);
		assertThat(queued()).isZero();
	}

	@Test
	void rolledBackTapLeavesNoOutboxRow() {
		parent(aryan, FATHER, true);
		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			markService.apply(office.getId(), List.of(new MarkRequest(aryan, EventType.BOARDED_MORNING, Outcome.DONE,
					java.time.LocalDate.parse(TODAY), java.time.Instant.parse("2026-10-07T02:12:00Z"))));
			assertThat(queued()).isEqualTo(1); // inside the transaction it is there
			tx.setRollbackOnly();
		});
		assertThat(rows()).isZero();
		assertThat(queued()).isZero();
	}

}
