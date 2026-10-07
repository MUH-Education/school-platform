package com.muhjain.school.trip;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.muhjain.school.messaging.OutboxWorker;
import com.muhjain.school.messaging.SendResult;
import com.muhjain.school.messaging.SmsSendException;
import com.muhjain.school.messaging.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Rules 8 to 11: the postman. The real provider is replaced by a mock, so nothing is ever sent. */
class OutboxWorkerTest extends TripTestBase {

	@Autowired
	private OutboxWorker worker;

	@MockitoBean
	private SmsSender smsSender;

	@BeforeEach
	void queueOneSms() throws Exception {
		long guardian = jdbc.queryForObject("insert into guardian (name, phone) values ('Father', '+919811100001') "
				+ "returning id", Long.class);
		jdbc.update("insert into student_guardian (student_id, guardian_id, relation) values (?, ?, 'FATHER')", aryan,
				guardian);
		sendMarks(balwanToken, morning(aryan, "DONE", "07:42:10"));
		assertThat(count("QUEUED")).isEqualTo(1);
	}

	private int count(String status) {
		return jdbc.queryForObject("select count(*) from message_outbox where status = ?", Integer.class, status);
	}

	@Test
	void workerMarksSentAndStoresProviderId() throws Exception {
		when(smsSender.send(any(), any(), any())).thenReturn(new SendResult("msg-77", false));
		assertThat(worker.runOnce()).isEqualTo(1);
		var row = jdbc.queryForMap("select status, provider_ref, sent_at, attempts, error from message_outbox");
		assertThat(row.get("status")).isEqualTo("SENT");
		assertThat(row.get("provider_ref")).isEqualTo("msg-77");
		assertThat(row.get("sent_at")).isNotNull();
		assertThat(row.get("attempts")).isEqualTo(0);
		verify(smsSender).send(eq("+919811100001"), eq("Aryan सुबह की बस में चढ़ गया — 7:42। MUH Jain School"), any());
		// A second round finds nothing to send.
		assertThat(worker.runOnce()).isZero();
		verify(smsSender, times(1)).send(any(), any(), any());
	}

	@Test
	void testModeBecomesTestOnly() throws Exception {
		when(smsSender.send(any(), any(), any())).thenReturn(new SendResult(null, true));
		worker.runOnce();
		assertThat(count("TEST_ONLY")).isEqualTo(1);
	}

	@Test
	void workerRetriesThreeTimesThenFails() throws Exception {
		when(smsSender.send(any(), any(), any())).thenThrow(new SmsSendException("invalid number"));
		worker.runOnce();
		assertThat(count("QUEUED")).isEqualTo(1);
		worker.runOnce();
		assertThat(count("QUEUED")).isEqualTo(1);
		worker.runOnce();
		assertThat(count("FAILED")).isEqualTo(1);
		var row = jdbc.queryForMap("select attempts, error from message_outbox");
		assertThat(row.get("attempts")).isEqualTo(3);
		assertThat(row.get("error")).isEqualTo("invalid number");
		worker.runOnce();
		verify(smsSender, times(3)).send(any(), any(), any());
	}

	@Test
	void oneFailingRowDoesNotStopTheOthers() throws Exception {
		sendMarks(balwanToken, morning(siya, "DONE", "07:43:00"));
		long guardian = jdbc.queryForObject("insert into guardian (name, phone) values ('Mother', '+919811100002') "
				+ "returning id", Long.class);
		jdbc.update("insert into student_guardian (student_id, guardian_id, relation) values (?, ?, 'MOTHER')", siya,
				guardian);
		sendMarks(balwanToken, morning(meera, "DONE", "07:44:00"));
		jdbc.update("delete from message_outbox");
		jdbc.update("insert into message_outbox (purpose, channel, phone, body, template_code) select 'BOARDING', 'SMS', "
				+ "'+91981110000' || g, 'text', null from generate_series(1, 3) g");
		when(smsSender.send(eq("+919811100001"), any(), any())).thenThrow(new SmsSendException("boom"));
		when(smsSender.send(eq("+919811100002"), any(), any())).thenReturn(new SendResult("ok", false));
		when(smsSender.send(eq("+919811100003"), any(), any())).thenReturn(new SendResult("ok", false));
		assertThat(worker.runOnce()).isEqualTo(3);
		assertThat(count("SENT")).isEqualTo(2);
		assertThat(count("QUEUED")).isEqualTo(1);
	}

	@Test
	void messageOlderThanTwoHoursIsNotSent() throws Exception {
		jdbc.update("update message_outbox set created_at = now() - interval '3 hours'");
		// The app clock is fixed in the test story (7 Oct 2026), so move the row relative to the app clock.
		jdbc.update("update message_outbox set created_at = ?::timestamptz", "2026-10-07T04:00:00+05:30");
		worker.runOnce();
		var row = jdbc.queryForMap("select status, error, attempts from message_outbox");
		assertThat(row.get("status")).isEqualTo("FAILED");
		assertThat(row.get("error")).isEqualTo("too old");
		verify(smsSender, never()).send(any(), any(), any());
	}

	@Test
	void messageJustUnderTwoHoursIsStillSent() throws Exception {
		when(smsSender.send(any(), any(), any())).thenReturn(new SendResult("x", false));
		jdbc.update("update message_outbox set created_at = ?::timestamptz", "2026-10-07T05:50:00+05:30");
		worker.runOnce();
		assertThat(count("SENT")).isEqualTo(1);
	}

	@Test
	void twoWorkersNeverSendTheSameRow() throws Exception {
		jdbc.update("delete from message_outbox");
		jdbc.update("insert into message_outbox (purpose, channel, phone, body) select 'BOARDING', 'SMS', "
				+ "'+9198111' || lpad(g::text, 5, '0'), 'text' from generate_series(1, 40) g");
		when(smsSender.send(any(), any(), any())).thenAnswer(inv -> {
			Thread.sleep(20);
			return new SendResult("ok", false);
		});
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> runs = List.of(pool.submit(() -> {
			go.await();
			return worker.runOnce();
		}), pool.submit(() -> {
			go.await();
			return worker.runOnce();
		}));
		go.countDown();
		int handled = 0;
		for (Future<Integer> run : runs) {
			handled += run.get();
		}
		pool.shutdown();
		// Every row once: together they handled exactly the 40 rows, and each phone was sent one time.
		assertThat(handled).isEqualTo(40);
		verify(smsSender, times(40)).send(any(), any(), any());
		assertThat(count("SENT")).isEqualTo(40);
		verify(smsSender, atLeast(1)).send(eq("+919811100001"), any(), any());
		verify(smsSender, times(1)).send(eq("+919811100001"), any(), any());
	}

}
