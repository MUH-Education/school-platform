package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.ButtonType;

/**
 * One button of a template. {@code fixed} = added by the server and cannot be removed (the "Stop messages" button of
 * a MARKETING or GREETING template). Example: {@code {"seqNo":3,"type":"REPLY","text":"Stop messages",
 * "action":"STOP_MESSAGES","fixed":true}}.
 */
public record TemplateButtonResponse(int seqNo, ButtonType type, String text, String action, boolean fixed) {
}
