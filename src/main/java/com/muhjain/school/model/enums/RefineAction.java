package com.muhjain.school.model.enums;

/**
 * What the AI "Refine" helper should do with the text. CUSTOM uses the office's own instruction.
 * SMS_FIT = make it fit one Hindi SMS (70 letters). The answer is only a suggestion.
 */
public enum RefineAction {

	SHORTER, BETTER_HINDI, WARMER, ENGLISH, FIX, SMS_FIT, CUSTOM

}
