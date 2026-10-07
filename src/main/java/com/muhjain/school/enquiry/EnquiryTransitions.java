package com.muhjain.school.enquiry;

import static com.muhjain.school.enquiry.EnquiryStatus.ADMITTED;
import static com.muhjain.school.enquiry.EnquiryStatus.APPLIED;
import static com.muhjain.school.enquiry.EnquiryStatus.CONTACTED;
import static com.muhjain.school.enquiry.EnquiryStatus.LOST;
import static com.muhjain.school.enquiry.EnquiryStatus.NEW;
import static com.muhjain.school.enquiry.EnquiryStatus.VISITED;

/**
 * Which stage changes a person may make by hand (rule 3). Pure Java.
 * <pre>
 * NEW, CONTACTED, VISITED, APPLIED   forward by one stage, or skipping one stage
 *                                    (a walk-in parent goes from NEW straight to VISITED)
 * any open stage → LOST              needs a reason
 * LOST → CONTACTED                   the enquiry is opened again
 * → ADMITTED                         never by hand: only an admission made from the enquiry does it
 * ADMITTED →                         nothing, the child is in the school
 * </pre>
 * Going back (VISITED → NEW) is not allowed. The same stage again is not a change (it is answered as "ok").
 */
public final class EnquiryTransitions {

	/** What happens when a person asks to move from one stage to another. */
	public enum Move {

		/** The change is allowed. */
		ALLOWED,
		/** Same stage: nothing to do. */
		UNCHANGED,
		/** Someone tried to set ADMITTED by hand. */
		ADMITTED_BY_HAND,
		/** Not a move this system has (backward, skipping two, anything from ADMITTED...). */
		NOT_ALLOWED

	}

	private EnquiryTransitions() {
	}

	public static Move check(EnquiryStatus from, EnquiryStatus to) {
		if (to == ADMITTED) {
			return (from == ADMITTED) ? Move.UNCHANGED : Move.ADMITTED_BY_HAND;
		}
		if (from == to) {
			return Move.UNCHANGED;
		}
		if (from == ADMITTED) {
			return Move.NOT_ALLOWED;
		}
		if (to == LOST) {
			return Move.ALLOWED;
		}
		if (from == LOST) {
			return (to == CONTACTED) ? Move.ALLOWED : Move.NOT_ALLOWED;
		}
		int jump = rank(to) - rank(from);
		return (jump == 1 || jump == 2) ? Move.ALLOWED : Move.NOT_ALLOWED;
	}

	// NEW 0, CONTACTED 1, VISITED 2, APPLIED 3. Only called for these four.
	private static int rank(EnquiryStatus status) {
		return switch (status) {
			case NEW -> 0;
			case CONTACTED -> 1;
			case VISITED -> 2;
			case APPLIED -> 3;
			default -> throw new IllegalArgumentException(status.name());
		};
	}

}
