package com.muhjain.school.setting;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.muhjain.school.audit.AuditAction;
import com.muhjain.school.audit.AuditService;
import com.muhjain.school.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * School-wide numbers. The keys are fixed by the Flyway file; this service only changes values.
 * Example: the owner sets "transport.bus_fee_per_year" from 8800 to 9000.
 */
@Service
public class SettingService {

	static final String ENTITY = "SETTING";

	private static final String TEXT_KEY = "school.name";

	private static final String PERCENT_KEY = "transport.collection_pct";

	private final AppSettingRepository settings;

	private final AuditService auditService;

	private final Clock clock;

	public SettingService(AppSettingRepository settings, AuditService auditService, Clock clock) {
		this.settings = settings;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<SettingResponse> list() {
		return settings.findAllByOrderByKeyAsc().stream().map(this::toResponse).toList();
	}

	/** For other features. Example: {@code value("transport.late_after_minutes")} → "10". */
	@Transactional(readOnly = true)
	public String value(String key) {
		return settings.findById(key).orElseThrow(() -> new IllegalArgumentException("No setting " + key)).getValue();
	}

	/**
	 * Changes some values. All or nothing: one bad value → 400 and nothing is saved.
	 *
	 * @param actorId the logged-in user, saved as {@code updated_by}
	 * @throws ApiException 400 VALIDATION for an unknown key or a bad value
	 */
	@Transactional
	public List<SettingResponse> update(UpdateSettingsRequest request, Long actorId) {
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		for (Map.Entry<String, String> entry : request.values().entrySet()) {
			String key = entry.getKey();
			AppSetting setting = settings.findById(key)
				.orElseThrow(() -> ApiException.validation(key, "is not a setting"));
			String value = checkValue(key, entry.getValue());
			if (value.equals(setting.getValue())) {
				continue;
			}
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("key", key);
			details.put("old", setting.getValue());
			details.put("new", value);
			auditService.record(ENTITY, 0L, AuditAction.UPDATED,
					key + " changed from " + setting.getValue() + " to " + value, details);
			setting.change(value, actorId, now);
		}
		return list();
	}

	// school.name is text. Every other setting is a whole number of 0 or more. A percent is at most 100.
	private static String checkValue(String key, String raw) {
		String value = (raw == null) ? "" : raw.strip();
		if (value.isEmpty()) {
			throw ApiException.validation(key, "must not be empty");
		}
		if (key.equals(TEXT_KEY)) {
			if (value.length() > 300) {
				throw ApiException.validation(key, "must be at most 300 characters");
			}
			return value;
		}
		long number;
		try {
			number = Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			throw ApiException.validation(key, "must be a whole number");
		}
		if (number < 0) {
			throw ApiException.validation(key, "must be 0 or more");
		}
		if (key.equals(PERCENT_KEY) && number > 100) {
			throw ApiException.validation(key, "must be at most 100");
		}
		return String.valueOf(number);
	}

	private SettingResponse toResponse(AppSetting setting) {
		return new SettingResponse(setting.getKey(), setting.getValue(),
				setting.getUpdatedAt().atZone(clock.getZone()).toOffsetDateTime(), setting.getUpdatedBy());
	}

}
