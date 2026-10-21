package com.muhjain.school.service.setting;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.request.UpdateSettingsRequest;
import com.muhjain.school.model.dto.response.SettingResponse;
import com.muhjain.school.model.entity.AppSetting;
import com.muhjain.school.model.enums.AuditAction;
import com.muhjain.school.model.repository.AppSettingRepository;
import com.muhjain.school.service.audit.AuditService;
import com.muhjain.school.util.PhoneNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * School-wide numbers. The keys are fixed by the Flyway file; this service only changes values.
 * Example: the owner sets "transport.bus_fee_per_year" from 8800 to 9000.
 */
@Service
public class SettingService {

	static final String ENTITY = "SETTING";

	// Free text, must not be blank.
	private static final Set<String> REQUIRED_TEXT_KEYS = Set.of("school.name", "school.short_name",
			"school.address");

	// Free text, may be left blank (they are seeded blank until the office fills them in).
	private static final Set<String> OPTIONAL_TEXT_KEYS = Set.of("school.principal_name", "school.affiliation_no",
			"school.school_code");

	private static final String PHONE_KEY = "school.office_phone";

	private static final String PERCENT_KEY = "transport.collection_pct";

	// A time of day in school time, "HH:mm". Example: quiet hours from "21:00" to "08:00".
	private static final Set<String> TIME_KEYS = Set.of("notifications.quiet_from", "notifications.quiet_to");

	private static final Pattern TIME = Pattern.compile("([01]?[0-9]|2[0-3]):([0-5][0-9])");

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

	// A few keys are free text (some may be left blank), one is a phone number, two are times of day, the rest
	// are whole numbers of 0 or more. A percent is at most 100.
	private static String checkValue(String key, String raw) {
		String value = (raw == null) ? "" : raw.strip();
		if (OPTIONAL_TEXT_KEYS.contains(key)) {
			if (value.length() > 300) {
				throw ApiException.validation(key, "must be at most 300 characters");
			}
			return value;
		}
		if (value.isEmpty()) {
			throw ApiException.validation(key, "must not be empty");
		}
		if (REQUIRED_TEXT_KEYS.contains(key)) {
			if (value.length() > 300) {
				throw ApiException.validation(key, "must be at most 300 characters");
			}
			return value;
		}
		if (key.equals(PHONE_KEY)) {
			try {
				return PhoneNumbers.normalize(value);
			}
			catch (ApiException ex) {
				throw ApiException.validation(key, "must be a valid Indian mobile number, like 98123 45678");
			}
		}
		if (TIME_KEYS.contains(key)) {
			Matcher time = TIME.matcher(value);
			if (!time.matches()) {
				throw ApiException.validation(key, "must be a time like 21:00");
			}
			// "9:00" is saved as "09:00", so every reader can compare the text.
			return String.format("%02d:%s", Integer.parseInt(time.group(1)), time.group(2));
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
