package com.muhjain.school.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.muhjain.school.model.enums.CancelReason;

/**
 * Cancels a whole receipt, by the owner only. Every line of the receipt gets a negative line that reverses
 * it (never edited or deleted), and the months it covered are unpaid again. The receipt itself stays, marked
 * CANCELLED with this reason and note.
 * Example: {@code {"receiptNo":"R-2026-0412","reason":"WRONG_AMOUNT","note":"Typed 7500, paid 7000"}}
 */
@Schema(example = "{\"receiptNo\": \"R-2026-0412\", \"reason\": \"WRONG_AMOUNT\", \"note\": \"Typed 7500, paid 7000\"}")
public record CorrectionRequest(@NotBlank String receiptNo, @NotNull CancelReason reason,
		@NotBlank @Size(max = 300) String note) {

}
