package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.RefineTextRequest;
import com.muhjain.school.model.dto.response.RefineInput;
import com.muhjain.school.model.dto.response.RefineResult;
import com.muhjain.school.model.enums.RefineAction;
import com.muhjain.school.util.Placeholders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * The "Refine" button (rule 11): asks the {@link TextRefiner} and checks the answer before the office sees it.
 * <ul>
 * <li>Every {box} of the text must still be there, written the same: else 409 REFINE_LOST_PLACEHOLDER and the
 * suggestion is dropped. Example: "{Child name}" became "आपके बच्चे" → refused.</li>
 * <li>The answer is trimmed and cut at 1024 letters (WhatsApp's limit).</li>
 * <li>The note counts letters, not bytes: "118 letters shorter".</li>
 * </ul>
 * Nothing is saved, nothing is logged (no text, no key).
 */
@Service
public class NotificationRefineService {

	static final int MAX_LETTERS = 1024;

	private final TextRefiner refiner;

	public NotificationRefineService(TextRefiner refiner) {
		this.refiner = refiner;
	}

	/**
	 * @throws ApiException 400 VALIDATION (CUSTOM with no instruction), 409 REFINE_LOST_PLACEHOLDER,
	 * 409 REFINE_NOT_CONFIGURED / REFINE_REFUSED / REFINE_FAILED, 503 REFINE_UNAVAILABLE
	 */
	public RefineResult refine(RefineTextRequest request) {
		if (request.action() == RefineAction.CUSTOM
				&& (request.instruction() == null || request.instruction().isBlank())) {
			throw ApiException.validation("instruction", "is needed for CUSTOM");
		}
		String text = request.text().strip();
		Set<String> boxes = Placeholders.of(text);
		String language = (request.language() == null) ? "hi" : request.language();
		RefineResult answer = refiner.refine(new RefineInput(text, request.action(), request.instruction(), language,
				boxes));
		String suggestion = cut(answer.text().strip());
		for (String box : boxes) {
			if (!suggestion.contains("{" + box + "}")) {
				throw new ApiException(HttpStatus.CONFLICT, "REFINE_LOST_PLACEHOLDER",
						"The suggestion lost {" + box + "}, so it was dropped. Try again or change the text yourself.");
			}
		}
		String note = (answer.note() != null) ? answer.note() : lengthNote(letters(text), letters(suggestion));
		return new RefineResult(suggestion, note);
	}

	// "118 letters shorter", "12 letters longer", "same length". Letters, not bytes: Hindi takes 3 bytes a letter.
	static String lengthNote(int before, int after) {
		int diff = before - after;
		if (diff == 0) {
			return "same length";
		}
		return Math.abs(diff) + ((Math.abs(diff) == 1) ? " letter " : " letters ") + ((diff > 0) ? "shorter" : "longer");
	}

	private static int letters(String text) {
		return text.codePointCount(0, text.length());
	}

	private static String cut(String text) {
		if (letters(text) <= MAX_LETTERS) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, MAX_LETTERS));
	}

}
