package com.muhjain.school.model.dto.response;

/** Children from one village. Example: {@code {"village":"Jakhal","students":58}}. */
public record VillageCount(String village, int students) {

}
