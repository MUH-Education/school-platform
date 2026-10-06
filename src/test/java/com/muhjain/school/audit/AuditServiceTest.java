package com.muhjain.school.audit;

import java.time.Instant;
import java.util.Map;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceTest extends AbstractIntegrationTest {

	@Autowired
	private AuditService audit;

	@Test
	void recordSavesOneRowWithJsonDetails() {
		Instant fixed = Instant.parse("2026-10-07T03:30:00Z");
		clock.setInstant(fixed);

		audit.record("USER", 2L, AuditAction.UPDATED, "Role changed from ADMISSIONS_DESK to OFFICE_ADMIN",
				Map.of("role", Map.of("old", "ADMISSIONS_DESK", "new", "OFFICE_ADMIN")));

		AuditLog row = audit.history("USER", 2L).getFirst();
		assertThat(row.getAction()).isEqualTo(AuditAction.UPDATED);
		assertThat(row.getChangedAt()).isEqualTo(fixed);
		assertThat(row.getChangedBy()).isNull();
		assertThat(row.getDetails()).isEqualTo(Map.of("role", Map.of("old", "ADMISSIONS_DESK", "new", "OFFICE_ADMIN")));
		assertThat(jdbc.queryForObject("select details->'role'->>'new' from audit_log", String.class))
			.isEqualTo("OFFICE_ADMIN");
	}

	@Test
	void longSummaryIsCutTo300Characters() {
		audit.record("USER", 3L, AuditAction.CREATED, "x".repeat(400), null);

		assertThat(audit.history("USER", 3L).getFirst().getSummary()).hasSize(300);
	}

}
