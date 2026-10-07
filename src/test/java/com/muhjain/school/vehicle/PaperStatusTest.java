package com.muhjain.school.vehicle;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Rule 3. "Today" is 7 Oct 2026 in every example. */
class PaperStatusTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	@Test
	void beforeTodayIsEnded() {
		assertThat(PaperStatus.of(LocalDate.of(2026, 10, 6), TODAY)).isEqualTo(PaperStatus.ENDED);
	}

	@Test
	void todayIsStillEndingSoon() {
		assertThat(PaperStatus.of(TODAY, TODAY)).isEqualTo(PaperStatus.ENDING_SOON);
	}

	@Test
	void thirtyDaysAheadIsEndingSoonAndThirtyOneIsValid() {
		assertThat(PaperStatus.of(TODAY.plusDays(30), TODAY)).isEqualTo(PaperStatus.ENDING_SOON);
		assertThat(PaperStatus.of(TODAY.plusDays(31), TODAY)).isEqualTo(PaperStatus.VALID);
	}

	@Test
	void noDateIsMissing() {
		assertThat(PaperStatus.of(null, TODAY)).isEqualTo(PaperStatus.MISSING);
	}

	@Test
	void worstPicksTheMostUrgent() {
		assertThat(PaperStatus.worst(List.of(PaperStatus.VALID, PaperStatus.ENDING_SOON, PaperStatus.MISSING)))
			.isEqualTo(PaperStatus.ENDING_SOON);
		assertThat(PaperStatus.worst(List.of(PaperStatus.VALID, PaperStatus.ENDED))).isEqualTo(PaperStatus.ENDED);
		assertThat(PaperStatus.worst(List.of(PaperStatus.VALID, PaperStatus.VALID))).isEqualTo(PaperStatus.VALID);
	}

}
