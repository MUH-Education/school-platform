package com.muhjain.school.setting;

import java.time.OffsetDateTime;

/** Example: {@code { "key": "transport.bus_fee_per_year", "value": "8800", "updatedAt": "...", "updatedBy": 1 }} */
public record SettingResponse(String key, String value, OffsetDateTime updatedAt, Long updatedBy) {

}
