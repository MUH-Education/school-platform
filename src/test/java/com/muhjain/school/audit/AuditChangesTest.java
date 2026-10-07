package com.muhjain.school.audit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChangesTest {

	@Test
	void unchangedValuesAreLeftOut() {
		AuditChanges changes = new AuditChanges().field("Seats", "seats", 14, 14).field("Name", "name", "Van 4", "Van 4");

		assertThat(changes.isEmpty()).isTrue();
		assertThat(changes.summary()).isEmpty();
		assertThat(changes.details()).isEmpty();
	}

	@Test
	void changedValueGivesOneLineAndOldNewDetails() {
		AuditChanges changes = new AuditChanges().field("Seats", "seats", 14, 26).field("Name", "name", "Van 4", "Van 4");

		assertThat(changes.summary()).isEqualTo("Seats changed from 14 to 26.");
		assertThat(changes.details()).containsOnlyKeys("seats");
		assertThat(changes.details().get("seats")).isEqualTo(Map.of("old", 14, "new", 26));
	}

	@Test
	void severalChangesAreJoinedInOrder() {
		AuditChanges changes = new AuditChanges().field("Name", "name", "Van 4", "Van 4A")
			.field("Seats", "seats", 14, 26)
			.active(true, false);

		assertThat(changes.summary()).isEqualTo("Name changed from Van 4 to Van 4A. Seats changed from 14 to 26. Turned off.");
		assertThat(changes.details()).containsOnlyKeys("name", "seats", "active");
	}

	@Test
	void moneyIsComparedAsANumberNotAsText() {
		AuditChanges same = new AuditChanges().field("Monthly cost", "monthlyCost", new BigDecimal("30300"),
				new BigDecimal("30300.00"));
		assertThat(same.isEmpty()).isTrue();

		AuditChanges changed = new AuditChanges().field("Monthly cost", "monthlyCost", new BigDecimal("30300.00"),
				new BigDecimal("31000.50"));
		assertThat(changed.summary()).isEqualTo("Monthly cost changed from 30300.00 to 31000.50.");
	}

	@Test
	void datesReadAsText() {
		AuditChanges changes = new AuditChanges().field("Insurance valid till", "insurance", LocalDate.of(2026, 10, 28),
				LocalDate.of(2027, 10, 27));

		assertThat(changes.summary()).isEqualTo("Insurance valid till changed from 28 Oct 2026 to 27 Oct 2027.");
		assertThat(changes.details().get("insurance")).isEqualTo(Map.of("old", "2026-10-28", "new", "2027-10-27"));
	}

	@Test
	void setAndRemovedAreWrittenInPlainWords() {
		assertThat(new AuditChanges().field("Insurance valid till", "insurance", null, LocalDate.of(2026, 10, 28)).summary())
			.isEqualTo("Insurance valid till set to 28 Oct 2026.");
		assertThat(new AuditChanges().field("PUC valid till", "puc", LocalDate.of(2027, 2, 1), null).summary())
			.isEqualTo("PUC valid till removed.");
	}

	@Test
	void turnedOnAndOff() {
		assertThat(new AuditChanges().active(false, true).summary()).isEqualTo("Turned on.");
		assertThat(new AuditChanges().active(true, false).summary()).isEqualTo("Turned off.");
		assertThat(new AuditChanges().active(true, true).isEmpty()).isTrue();
	}

	@Test
	void maskedFieldNeverShowsTheFullValue() {
		AuditChanges changes = new AuditChanges().maskedField("Phone", "phone", "+919812340010", "+919812340012",
				p -> "+91XXXXXX" + p.substring(9));

		assertThat(changes.summary()).isEqualTo("Phone changed from +91XXXXXX0010 to +91XXXXXX0012.");
		assertThat(changes.details().toString()).doesNotContain("9812340010");
	}

}
