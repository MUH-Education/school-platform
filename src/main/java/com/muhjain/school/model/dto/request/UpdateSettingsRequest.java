package com.muhjain.school.model.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * Only the settings to change. Example: {@code { "values": { "transport.bus_fee_per_year": "9000" } }}
 */
public record UpdateSettingsRequest(@NotEmpty Map<String, String> values) {

}
