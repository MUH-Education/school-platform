package com.muhjain.school.student;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.common.PhoneNumbers;
import com.muhjain.school.route.RouteResponse;
import com.muhjain.school.route.RouteService;
import com.muhjain.school.route.StopResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import of the existing students from a CSV file. Rules 24 to 26 of docs/phases/phase-3-students-admission.md.
 * <p>
 * Columns: {@code name, gender, dob, class, section, village, father_occupation, parent_name, parent_phone, route,
 * stop}. The first line is the header. Example line:
 * {@code Aryan,M,14/05/2018,3,B,Jakhal,FARMER_SMALL,Ramesh,98123 40208,Route 4,Jakhal}
 * <p>
 * <b>Dry run</b> checks every line and saves nothing. A real run saves every good line, each in its own
 * transaction (through {@link AdmissionService#admit}), and skips the bad ones. So one bad line never stops the
 * others, and a run can be repeated: a child who is already saved (same name, date of birth and phone) is reported
 * as an error and not saved twice.
 * <p>
 * This class is <b>not</b> {@code @Transactional}, on purpose: each line commits alone.
 */
@Service
public class StudentImportService {

	private static final Logger log = LoggerFactory.getLogger(StudentImportService.class);

	private static final int MAX_LINES = 5000;

	private static final List<String> REQUIRED = List.of("name", "gender", "dob", "class", "village", "parent_phone");

	private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
			strict("d/M/uuuu"), strict("d-M-uuuu"), strict("d.M.uuuu"));

	// Strict: 31/02/2015 is an error, not 28 Feb.
	private static DateTimeFormatter strict(String pattern) {
		return DateTimeFormatter.ofPattern(pattern).withResolverStyle(java.time.format.ResolverStyle.STRICT);
	}

	private static final Pattern DIGITS_ONLY = Pattern.compile("\\D");

	private final AdmissionService admissionService;

	private final StudentRepository students;

	private final RouteService routeService;

	private final Clock clock;

	public StudentImportService(AdmissionService admissionService, StudentRepository students,
			RouteService routeService, Clock clock) {
		this.admissionService = admissionService;
		this.students = students;
		this.routeService = routeService;
		this.clock = clock;
	}

	/**
	 * @param file the bytes of the CSV file, UTF-8
	 * @param userId the logged-in user, from the token
	 * @throws ApiException 400 VALIDATION only for a problem with the whole file (empty, no header, a column is
	 * missing, too many lines). A problem with one line is an {@link ImportError} in the answer.
	 */
	public ImportResponse importCsv(byte[] file, boolean dryRun, Long userId) {
		List<CsvReader.Row> rows = CsvReader.parse(new String(file, StandardCharsets.UTF_8));
		rows.removeIf(CsvReader.Row::isBlank);
		if (rows.isEmpty()) {
			throw ApiException.validation("file", "is empty");
		}
		Map<String, Integer> columns = header(rows.getFirst());
		List<CsvReader.Row> lines = rows.subList(1, rows.size());
		if (lines.size() > MAX_LINES) {
			throw ApiException.validation("file", "has more than " + MAX_LINES + " lines. Split it in two files.");
		}

		Routes routes = new Routes(routeService.list());
		LocalDate today = LocalDate.now(clock);
		LocalDate joinedOn = schoolYearStart(today);
		Set<String> seenInFile = new HashSet<>();
		List<ImportError> errors = new ArrayList<>();
		int created = 0;

		for (CsvReader.Row row : lines) {
			List<String> problems = new ArrayList<>();
			AdmissionRequest request = read(row, columns, routes, today, joinedOn, problems);
			if (request != null && problems.isEmpty()) {
				String phone = PhoneNumbers.normalize(request.guardians().getFirst().phone());
				String key = request.name().toLowerCase(Locale.ROOT) + "|" + request.dob() + "|" + phone;
				if (!seenInFile.add(key)) {
					problems.add("The same child is on an earlier line of this file");
				}
				else if (students.existsSameChild(request.name().toLowerCase(Locale.ROOT), request.dob(), phone)) {
					problems.add("This child is already saved (same name, date of birth and phone)");
				}
			}
			if (!problems.isEmpty()) {
				errors.add(new ImportError(row.line(), String.join("; ", problems)));
				continue;
			}
			if (dryRun) {
				created++;
				continue;
			}
			try {
				admissionService.admit(request, userId);
				created++;
			}
			catch (ApiException ex) {
				errors.add(new ImportError(row.line(), ex.getMessage()));
			}
			catch (RuntimeException ex) {
				// The details go to the log. The line number is enough for the clerk.
				log.error("Import: line {} could not be saved", row.line(), ex);
				errors.add(new ImportError(row.line(), "This line could not be saved"));
			}
		}
		log.info("Student import finished: dryRun={}, created={}, errors={}", dryRun, created, errors.size());
		return new ImportResponse(dryRun, created, errors);
	}

	// ---- the header ----

	private static Map<String, Integer> header(CsvReader.Row row) {
		Map<String, Integer> columns = new HashMap<>();
		for (int i = 0; i < row.cells().size(); i++) {
			String name = row.cell(i).toLowerCase(Locale.ROOT).replace(' ', '_');
			if (name.equals("class_name")) {
				name = "class";
			}
			columns.putIfAbsent(name, i);
		}
		List<String> missing = REQUIRED.stream().filter(c -> !columns.containsKey(c)).toList();
		if (!missing.isEmpty()) {
			throw ApiException.validation("file",
					"the first line must be the column names. These are missing: " + String.join(", ", missing));
		}
		return columns;
	}

	// ---- one line ----

	// Collects every problem of the line. Returns the request, or null if the line is too broken to build one.
	private AdmissionRequest read(CsvReader.Row row, Map<String, Integer> columns, Routes routes, LocalDate today,
			LocalDate joinedOn, List<String> problems) {
		String name = NameKeys.tidy(value(row, columns, "name"));
		if (name.isEmpty()) {
			problems.add("Name is missing");
		}
		else if (name.length() > 120) {
			problems.add("Name is longer than 120 letters");
		}

		Gender gender = gender(value(row, columns, "gender"), problems);
		LocalDate dob = dob(value(row, columns, "dob"), today, problems);

		String rawClass = value(row, columns, "class");
		String className = ClassNames.parse(rawClass).orElse(null);
		if (className == null) {
			problems.add(rawClass.isEmpty() ? "Class is missing"
					: "Class '" + rawClass + "' must be Nursery, LKG, UKG or 1 to 12");
		}

		String section = value(row, columns, "section");
		if (section.length() > 4) {
			problems.add("Section is longer than 4 letters");
		}

		String village = NameKeys.tidy(value(row, columns, "village"));
		if (village.isEmpty()) {
			problems.add("Village is missing");
		}
		else if (village.length() > 80) {
			problems.add("Village is longer than 80 letters");
		}

		FatherOccupation occupation = occupation(value(row, columns, "father_occupation"), problems);

		String parentName = NameKeys.tidy(value(row, columns, "parent_name"));
		if (parentName.length() > 120) {
			problems.add("Parent name is longer than 120 letters");
		}
		String phone = phone(value(row, columns, "parent_phone"), problems);

		AdmissionBus bus = bus(value(row, columns, "route"), value(row, columns, "stop"), joinedOn, routes, problems);

		if (!problems.isEmpty()) {
			return null;
		}
		// The sheet does not say who the phone belongs to, so the relation is OTHER. The office can change it later.
		GuardianRequest guardian = new GuardianRequest(parentName.isEmpty() ? null : parentName, phone,
				GuardianRelation.OTHER, true);
		return new AdmissionRequest(name, dob, gender, className, section.isEmpty() ? null : section, village, null,
				occupation, joinedOn, List.of(guardian), null, null, bus);
	}

	private static String value(CsvReader.Row row, Map<String, Integer> columns, String column) {
		Integer index = columns.get(column);
		return (index == null) ? "" : row.cell(index);
	}

	private static Gender gender(String text, List<String> problems) {
		switch (text.toLowerCase(Locale.ROOT)) {
			case "m", "male", "boy" -> {
				return Gender.M;
			}
			case "f", "female", "girl" -> {
				return Gender.F;
			}
			default -> {
				problems.add(text.isEmpty() ? "Gender is missing" : "Gender '" + text + "' must be M or F");
				return null;
			}
		}
	}

	// Excel writes 14/05/2018 or 14-05-2018. The ISO form 2018-05-14 is fine too.
	private static LocalDate dob(String text, LocalDate today, List<String> problems) {
		if (text.isEmpty()) {
			problems.add("Date of birth is missing");
			return null;
		}
		for (DateTimeFormatter format : DATE_FORMATS) {
			try {
				LocalDate date = LocalDate.parse(text, format);
				if (date.isAfter(today)) {
					problems.add("Date of birth is in the future");
					return null;
				}
				return date;
			}
			catch (DateTimeParseException ex) {
				// try the next format
			}
		}
		problems.add("Date of birth '" + text + "' is not a date. Use day/month/year, like 14/05/2018");
		return null;
	}

	// A blank father_occupation is saved as OTHER. The sheet of an old student often has no such column.
	private static FatherOccupation occupation(String text, List<String> problems) {
		if (text.isEmpty()) {
			return FatherOccupation.OTHER;
		}
		String wanted = text.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
		for (FatherOccupation value : FatherOccupation.values()) {
			if (value.name().equals(wanted)) {
				return value;
			}
		}
		problems.add("Father occupation '" + text + "' is not in the list: "
				+ String.join(", ", java.util.Arrays.stream(FatherOccupation.values()).map(Enum::name).toList()));
		return null;
	}

	// Example message: "Phone has 9 digits".
	private static String phone(String text, List<String> problems) {
		if (text.isEmpty()) {
			problems.add("Phone is missing");
			return null;
		}
		String digits = DIGITS_ONLY.matcher(text).replaceAll("");
		if (digits.length() == 12 && digits.startsWith("91")) {
			digits = digits.substring(2);
		}
		else if (digits.length() == 11 && digits.startsWith("0")) {
			digits = digits.substring(1);
		}
		if (digits.length() != 10) {
			problems.add("Phone has " + digits.length() + " digits");
			return null;
		}
		try {
			return PhoneNumbers.normalize(digits);
		}
		catch (ApiException ex) {
			problems.add("Phone must start with 6, 7, 8 or 9");
			return null;
		}
	}

	// Route and stop go together. Both empty = no bus.
	private static AdmissionBus bus(String routeText, String stopText, LocalDate from, Routes routes,
			List<String> problems) {
		if (routeText.isEmpty() && stopText.isEmpty()) {
			return null;
		}
		if (routeText.isEmpty()) {
			problems.add("Route is missing (a stop is given)");
			return null;
		}
		if (stopText.isEmpty()) {
			problems.add("Stop is missing (a route is given)");
			return null;
		}
		Optional<RouteResponse> route = routes.find(routeText);
		if (route.isEmpty()) {
			problems.add("Route '" + routeText + "' does not exist or is turned off");
			return null;
		}
		Optional<StopResponse> stop = route.get()
			.stops()
			.stream()
			.filter(s -> NameKeys.key(s.name()).equals(NameKeys.key(stopText)))
			.findFirst();
		if (stop.isEmpty()) {
			problems.add("Stop '" + stopText + "' is not a stop of " + route.get().name());
			return null;
		}
		return new AdmissionBus(route.get().id(), stop.get().id(), from, null);
	}

	// Existing students joined before this school year. 1 April of the current school year is the best guess.
	private static LocalDate schoolYearStart(LocalDate today) {
		return LocalDate.of((today.getMonthValue() >= 4) ? today.getYear() : today.getYear() - 1, 4, 1);
	}

	// The routes that are turned on, found by name without caring about capital letters or spaces.
	private static final class Routes {

		private final Map<String, RouteResponse> byKey = new HashMap<>();

		Routes(List<RouteResponse> all) {
			all.stream().filter(RouteResponse::active).forEach(r -> byKey.put(NameKeys.key(r.name()), r));
		}

		Optional<RouteResponse> find(String name) {
			return Optional.ofNullable(byKey.get(NameKeys.key(name)));
		}

	}

}
