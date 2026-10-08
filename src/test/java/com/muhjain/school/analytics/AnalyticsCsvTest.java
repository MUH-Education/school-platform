package com.muhjain.school.analytics;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsCsvTest extends AnalyticsTestBase {

	private static final String URL = "/api/v1/analytics/students.csv";

	private byte[] download(String token, String query) throws Exception {
		return get(token, URL + query).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"students-2026-10-07.csv\""))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
	}

	private static List<String> lines(byte[] file) {
		return List.of(new String(file, StandardCharsets.UTF_8).substring(1).split("\r\n"));
	}

	@Test
	void csvHasAllRowsNotOnlyOnePage() throws Exception {
		// size=5 is a list setting; the file ignores it and has every matching child.
		byte[] file = download(owner, "?size=5&page=1");
		List<String> lines = lines(file);
		assertThat(lines).hasSize(13);
		assertThat(lines.get(0)).isEqualTo("Name,Class,Village,Father's occupation,Bus route,School fee status,"
				+ "Bus fee status,Pending amount");
		assertThat(lines.get(1)).isEqualTo("Aarav,1,Jakhal,FARMER_SMALL,Route A,ON_TIME,ON_TIME,0.00");
		assertThat(lines).contains("Charu,2,Jakhal,GOVT_EMPLOYEE,Route A,ON_TIME,DEFAULTED,3500.00",
				"Bhavya,1,Jakhal,FARMER_SMALL,,DELAYED,,2000.00", "Farhan,3,Dhand,LABOUR,,,,0.00");
	}

	@Test
	void fileStartsWithAUtf8Bom() throws Exception {
		byte[] file = download(office, "");
		assertThat(file[0]).isEqualTo((byte) 0xEF);
		assertThat(file[1]).isEqualTo((byte) 0xBB);
		assertThat(file[2]).isEqualTo((byte) 0xBF);
	}

	@Test
	void csvFollowsTheSameFilterAsTheList() throws Exception {
		List<String> lines = lines(download(desk, "?village=Jakhal&feeStatus=DELAYED"));
		assertThat(lines).hasSize(2);
		assertThat(lines.get(1)).startsWith("Bhavya,");
	}

	@Test
	void hindiNamesAndCommasSurvive() throws Exception {
		jdbc.update("update student set name = 'आरव' where name = 'Aarav'");
		jdbc.update("update student set village = 'Kalwa, Tohana' where name = 'Dev'");
		byte[] file = download(owner, "");
		String text = new String(file, StandardCharsets.UTF_8);
		assertThat(text).contains("आरव,1,Jakhal").contains("\"Kalwa, Tohana\"");
	}

	@Test
	void aCellThatLooksLikeAFormulaGetsAQuote() throws Exception {
		jdbc.update("update student set name = '=HYPERLINK(\"http://bad\")' where name = 'Dev'");
		jdbc.update("update student set name = '@SUM(1)' where name = 'Esha'");
		jdbc.update("update student set name = '-2+3' where name = 'Farhan'");
		jdbc.update("update student set name = '+1' where name = 'Gauri'");
		String text = new String(download(owner, ""), StandardCharsets.UTF_8);
		assertThat(text).contains("\"'=HYPERLINK(\"\"http://bad\"\")\",2,Kalwa")
			.contains("'@SUM(1),3,Kalwa")
			.contains("'-2+3,3,Dhand")
			.contains("'+1,5,Dhand");
		assertThat(text).doesNotContain("\n=").doesNotContain("\n@").doesNotContain("\n-").doesNotContain("\n+");
	}

	@Test
	void cellRulesInOnePlace() {
		assertThat(AnalyticsCsv.cell(null)).isEmpty();
		assertThat(AnalyticsCsv.cell("Aryan")).isEqualTo("Aryan");
		assertThat(AnalyticsCsv.cell("=1+1")).isEqualTo("'=1+1");
		assertThat(AnalyticsCsv.cell("\tx")).isEqualTo("'\tx");
		assertThat(AnalyticsCsv.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
		assertThat(AnalyticsCsv.cell("two\nlines")).isEqualTo("\"two\nlines\"");
		assertThat(AnalyticsCsv.cell("1-5")).isEqualTo("1-5");
	}

	@Test
	void csvDownloadIsAudited() throws Exception {
		long ownerId = jdbc.queryForObject("select id from app_user where phone = '+919812340001'", Long.class);
		long before = jdbc.queryForObject("select count(*) from audit_log where entity_type = 'ANALYTICS_CSV'",
				Long.class);
		assertThat(before).isZero();

		download(owner, "?village=Jakhal&className=1-5&feeStatus=DELAYED");

		Map<String, Object> row = jdbc.queryForMap("select entity_id, action, summary, changed_by, changed_at, "
				+ "details->>'village' as village, details->>'className' as class_name, "
				+ "details->>'feeStatus' as fee_status, details->>'rows' as row_count "
				+ "from audit_log where entity_type = 'ANALYTICS_CSV'");
		assertThat(row.get("changed_by")).isEqualTo(ownerId);
		assertThat(row.get("entity_id")).isEqualTo(ownerId);
		assertThat(row.get("action")).isEqualTo("CREATED");
		assertThat(row.get("summary")).isEqualTo("Students CSV downloaded: 1 rows, school year 2026-27");
		assertThat(row.get("village")).isEqualTo("Jakhal");
		assertThat(row.get("class_name")).isEqualTo("1,2,3,4,5");
		assertThat(row.get("fee_status")).isEqualTo("DELAYED");
		assertThat(row.get("row_count")).isEqualTo("1");
		assertThat(row.get("changed_at")).isNotNull();

		download(desk, "");
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'ANALYTICS_CSV'",
				Long.class)).isEqualTo(2L);
	}

	@Test
	void aRefusedOrBadRequestWritesNoAuditRow() throws Exception {
		get(transport, URL).andExpect(status().isForbidden());
		get(owner, URL + "?className=7th").andExpect(status().isBadRequest());
		get(owner, URL + "?sessionId=9999").andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'ANALYTICS_CSV'",
				Long.class)).isZero();
	}

	@Test
	void emptyResultIsOnlyTheHeader() throws Exception {
		assertThat(lines(download(owner, "?className=12"))).hasSize(1);
	}

	@Test
	void csvNeverHasAPhoneNumber() throws Exception {
		String text = new String(download(owner, ""), StandardCharsets.UTF_8);
		assertThat(text).doesNotContain("+91").doesNotContainPattern("\\d{10}");
	}

}
