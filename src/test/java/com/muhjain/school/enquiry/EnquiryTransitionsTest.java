package com.muhjain.school.enquiry;

import com.muhjain.school.util.EnquiryTransitions.Move;
import org.junit.jupiter.api.Test;

import static com.muhjain.school.model.enums.EnquiryStatus.ADMITTED;
import static com.muhjain.school.model.enums.EnquiryStatus.APPLIED;
import static com.muhjain.school.model.enums.EnquiryStatus.CONTACTED;
import static com.muhjain.school.model.enums.EnquiryStatus.LOST;
import static com.muhjain.school.model.enums.EnquiryStatus.NEW;
import static com.muhjain.school.model.enums.EnquiryStatus.VISITED;
import static com.muhjain.school.util.EnquiryTransitions.Move.ADMITTED_BY_HAND;
import static com.muhjain.school.util.EnquiryTransitions.Move.ALLOWED;
import static com.muhjain.school.util.EnquiryTransitions.Move.NOT_ALLOWED;
import static com.muhjain.school.util.EnquiryTransitions.Move.UNCHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import com.muhjain.school.model.enums.EnquiryStatus;
import com.muhjain.school.util.EnquiryTransitions;

/** The whole table: 6 stages x 6 stages. */
class EnquiryTransitionsTest {

	private static final EnquiryStatus[] ORDER = { NEW, CONTACTED, VISITED, APPLIED, ADMITTED, LOST };

	// Rows = from, columns = to, in the order NEW CONTACTED VISITED APPLIED ADMITTED LOST.
	private static final Move[][] TABLE = {
			/* NEW       */ { UNCHANGED, ALLOWED, ALLOWED, NOT_ALLOWED, ADMITTED_BY_HAND, ALLOWED },
			/* CONTACTED */ { NOT_ALLOWED, UNCHANGED, ALLOWED, ALLOWED, ADMITTED_BY_HAND, ALLOWED },
			/* VISITED   */ { NOT_ALLOWED, NOT_ALLOWED, UNCHANGED, ALLOWED, ADMITTED_BY_HAND, ALLOWED },
			/* APPLIED   */ { NOT_ALLOWED, NOT_ALLOWED, NOT_ALLOWED, UNCHANGED, ADMITTED_BY_HAND, ALLOWED },
			/* ADMITTED  */ { NOT_ALLOWED, NOT_ALLOWED, NOT_ALLOWED, NOT_ALLOWED, UNCHANGED, NOT_ALLOWED },
			// Which exact stage a reopen may land on is checked by the caller (status_before_lost), not here.
			/* LOST      */ { ALLOWED, ALLOWED, ALLOWED, ALLOWED, ADMITTED_BY_HAND, UNCHANGED } };

	@Test
	void everyMoveFollowsTheTable() {
		for (int from = 0; from < ORDER.length; from++) {
			for (int to = 0; to < ORDER.length; to++) {
				assertThat(EnquiryTransitions.check(ORDER[from], ORDER[to])).as(ORDER[from] + " → " + ORDER[to])
					.isEqualTo(TABLE[from][to]);
			}
		}
	}

}
