package com.muhjain.school.vehicle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.common.NameKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private VehicleRepository vehicles;

	@Autowired
	private VehicleDocumentRepository documents;

	private Vehicle van4() {
		return new Vehicle("Van 4", "HR 23 A 1104", VehicleType.SMALL_VAN, 14, new BigDecimal("30300.00"),
				OwnedBy.CONTRACTOR);
	}

	@Test
	void savedVehicleKeepsMoneyAsBigDecimalAndTimesFromTheAppClock() {
		Instant fixed = Instant.parse("2026-10-07T03:30:00Z");
		clock.setInstant(fixed);

		Vehicle saved = vehicles.saveAndFlush(van4());

		Vehicle loaded = vehicles.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getMonthlyCost()).isEqualByComparingTo("30300.00");
		assertThat(loaded.getVehicleType()).isEqualTo(VehicleType.SMALL_VAN);
		assertThat(loaded.isActive()).isTrue();
		assertThat(loaded.getCreatedAt()).isEqualTo(fixed);
	}

	@Test
	void nameAndRegistrationAreFoundIgnoringCaseAndSpaces() {
		Vehicle saved = vehicles.saveAndFlush(van4());

		assertThat(vehicles.existsByNameKey(NameKeys.key("van4"), 0L)).isTrue();
		assertThat(vehicles.existsByRegistrationKey(NameKeys.key("hr 23 a 1104"), 0L)).isTrue();
		assertThat(vehicles.existsByNameKey(NameKeys.key("Van 5"), 0L)).isFalse();
		// A vehicle does not clash with itself (when it is edited).
		assertThat(vehicles.existsByNameKey(NameKeys.key("Van 4"), saved.getId())).isFalse();
	}

	@Test
	void databaseRejectsTheSameRegistrationWithOtherSpacing() {
		vehicles.saveAndFlush(van4());
		Vehicle twin = new Vehicle("Van 9", "hr23a1104", VehicleType.SMALL_VAN, 14, BigDecimal.TEN,
				OwnedBy.SCHOOL);

		assertThatThrownBy(() -> vehicles.saveAndFlush(twin)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void documentsAreFoundByVehicleAndByEndDate() {
		Vehicle saved = vehicles.saveAndFlush(van4());
		documents.saveAndFlush(new VehicleDocument(saved.getId(), DocType.INSURANCE, LocalDate.of(2026, 10, 28)));
		documents.saveAndFlush(new VehicleDocument(saved.getId(), DocType.PUC, LocalDate.of(2027, 3, 1)));

		assertThat(documents.findByVehicleId(saved.getId())).hasSize(2);
		assertThat(documents.findByValidTillLessThanEqualOrderByValidTillAscIdAsc(LocalDate.of(2026, 11, 6)))
			.extracting(VehicleDocument::getDocType)
			.containsExactly(DocType.INSURANCE);
	}

	@Test
	@Transactional
	void lockedLookupFindsTheVehicle() {
		Vehicle saved = vehicles.saveAndFlush(van4());

		// A row lock needs a transaction. This one is rolled back at the end of the test.
		assertThat(vehicles.findByIdForUpdate(saved.getId())).isPresent();
	}

}
