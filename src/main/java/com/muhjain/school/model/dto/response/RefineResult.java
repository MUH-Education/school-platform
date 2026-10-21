package com.muhjain.school.model.dto.response;

/**
 * A suggestion: the new text and a short note for the office. Nothing is saved; the office clicks "Use this" or not.
 * Example: {@code {"text": "{Parent name} जी, PTM शनिवार 10 बजे।", "note": "41 letters shorter"}}.
 */
public record RefineResult(String text, String note) {
}
