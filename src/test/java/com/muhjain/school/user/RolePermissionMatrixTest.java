package com.muhjain.school.user;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the table "Which role has which permission" in docs/05-roles-permissions.md and checks the enums,
 * cell by cell. Example row: {@code | `ROUTES_EDIT` | yes | | yes | | |} means OWNER and TRANSPORT_INCHARGE.
 */
class RolePermissionMatrixTest {

	private static final Path DOC = Path.of("docs/05-roles-permissions.md");

	// Column order of the table in the doc.
	private static final List<Role> COLUMNS = List.of(Role.OWNER, Role.OFFICE_ADMIN, Role.TRANSPORT_INCHARGE,
			Role.ADMISSIONS_DESK, Role.ATTENDANT);

	private static Map<String, List<String>> table;

	@BeforeAll
	static void readTableFromDoc() throws IOException {
		List<String> lines = Files.readAllLines(DOC);
		int start = lines.indexOf("## Which role has which permission");
		assertThat(start).as("table heading in %s", DOC).isPositive();
		table = new LinkedHashMap<>();
		for (String line : lines.subList(start + 1, lines.size())) {
			if (line.startsWith("## ")) {
				break;
			}
			if (!line.startsWith("| `")) {
				continue;
			}
			// "| `ROUTES_EDIT` | yes | | yes | | |" → ["`ROUTES_EDIT`", "yes", "", "yes", "", ""]
			String[] cells = line.substring(1, line.length() - 1).split("\\|", -1);
			List<String> values = new ArrayList<>();
			for (String cell : Arrays.copyOfRange(cells, 1, cells.length)) {
				values.add(cell.strip());
			}
			table.put(cells[0].strip().replace("`", ""), values);
		}
	}

	@Test
	void docHasOneRowForEachPermissionInTheSameOrder() {
		assertThat(table.keySet()).containsExactly(Arrays.stream(Permission.values()).map(Enum::name).toArray(String[]::new));
	}

	@Test
	void everyCellMatches() {
		for (Map.Entry<String, List<String>> row : table.entrySet()) {
			Permission permission = Permission.valueOf(row.getKey());
			assertThat(row.getValue()).as("cells of %s", permission).hasSize(COLUMNS.size());
			for (int i = 0; i < COLUMNS.size(); i++) {
				Role role = COLUMNS.get(i);
				String cell = row.getValue().get(i);
				assertThat(cell).as("cell %s / %s", permission, role).isIn("yes", "");
				assertThat(role.has(permission)).as("%s has %s", role, permission).isEqualTo(cell.equals("yes"));
			}
		}
	}

	@Test
	void fiveRolesAndOwnerHasEverything() {
		assertThat(Role.values()).containsExactlyElementsOf(COLUMNS);
		assertThat(Role.OWNER.permissions()).containsExactlyInAnyOrder(Permission.values());
		assertThat(Role.ATTENDANT.permissions()).containsExactly(Permission.TRIPS_RECORD);
	}

}
