package com.muhjain.school.setting;

import java.util.Map;

import jakarta.validation.constraints.NotEmpty;

/**
 * Only the settings to change. Example: {@code { "values": { "transport.bus_fee_per_year": "9000" } }}
 */
public record UpdateSettingsRequest(@NotEmpty Map<String, String> values) {

}
