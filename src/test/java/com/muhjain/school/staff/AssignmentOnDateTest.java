package com.muhjain.school.staff;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code AssignmentService.onDate} with a real database. The rule itself is in {@link AssignmentRulesTest}. */
class AssignmentOnDateTest extends AbstractIntegrationTest {

	@Autowired
	private AssignmentService assignmentService;

	private long van4;

	private long van1;

	private long jagdish;

	private long surender;

	private long balwan;

	@BeforeEach
	void addCrew() {
		van4 = addVehicle("Van 4");
		van1 = addVehicle("Van 1");
		jagdish = addStaff("Jagdish", "DRIVER");
		surender = addStaff("Surender", "DRIVER");
		balwan = addStaff("Balwan", "ATTENDANT");
		addAssignment(van4, jagdish, "DRIVER", "2026-04-01", null, false);
		addAssignment(van4, balwan, "ATTENDANT", "2026-04-01", null, false);
		addAssignment(van4, surender, "DRIVER", "2026-10-12", "2026-10-16", true);
	}

	@Test
	void insideTheLeaveTheTemporaryDriverIsOnTheVehicle() {
		Crew crew = assignmentService.onDate(van4, LocalDate.of(2026, 10, 14));

		assertThat(crew.driver().staffId()).isEqualTo(surender);
		assertThat(crew.driver().name()).isEqualTo("Surender");
		assertThat(crew.driver().phone()).isEqualTo("+919811100000");
		assertThat(crew.driver().temporary()).isTrue();
		assertThat(crew.attendant().staffId()).isEqualTo(balwan);
		assertThat(crew.attendant().temporary()).isFalse();
		assertThat(crew.helper()).isNull();
	}

	@Test
	void outsideTheLeaveThePermanentDriverIsOnTheVehicle() {
		assertThat(assignmentService.onDate(van4, LocalDate.of(2026, 10, 11)).driver().staffId()).isEqualTo(jagdish);
		assertThat(assignmentService.onDate(van4, LocalDate.of(2026, 10, 17)).driver().staffId()).isEqualTo(jagdish);
		assertThat(assignmentService.onDate(van4, LocalDate.of(2026, 10, 17)).driver().temporary()).isFalse();
	}

	@Test
	void vehicleWithNobodyHasAnEmptyCrew() {
		assertThat(assignmentService.onDate(van1, LocalDate.of(2026, 10, 14))).isEqualTo(Crew.EMPTY);
		assertThat(assignmentService.onDate(999L, LocalDate.of(2026, 10, 14))).isEqualTo(Crew.EMPTY);
		assertThat(assignmentService.onDate(van4, LocalDate.of(2026, 3, 31))).isEqualTo(Crew.EMPTY);
	}

	@Test
	void manyVehiclesAtOnce() {
		Map<Long, Crew> crews = assignmentService.onDate(List.of(van4, van1), LocalDate.of(2026, 10, 14));

		assertThat(crews).containsOnlyKeys(van4, van1);
		assertThat(crews.get(van4).driver().staffId()).isEqualTo(surender);
		assertThat(crews.get(van1)).isEqualTo(Crew.EMPTY);
		assertThat(assignmentService.onDate(List.<Long>of(), LocalDate.of(2026, 10, 14))).isEmpty();
	}

}
