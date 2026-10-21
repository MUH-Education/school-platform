package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.RefineAction;

import java.util.Set;

/**
 * What a {@code TextRefiner} gets. {@code placeholders} are the {boxes} of the text, which must all survive.
 * Example: "नमस्ते {Parent name}, PTM शनिवार को है ...", SHORTER, null, hi, [Parent name].
 */
public record RefineInput(String text, RefineAction action, String instruction, String language,
		Set<String> placeholders) {
}
