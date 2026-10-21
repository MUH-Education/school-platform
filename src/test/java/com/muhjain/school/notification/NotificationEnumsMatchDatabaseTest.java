package com.muhjain.school.notification;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.enums.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every Java enum of the Notification module has exactly the values the database {@code check} allows. A value
 * added on one side only would fail at the first save, in production; this test fails at once instead.
 */
class NotificationEnumsMatchDatabaseTest extends AbstractIntegrationTest {

	private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");

	/** The quoted words of the check constraints on this column. Example: {'DRAFT', 'SCHEDULED', ...}. */
	private Set<String> allowed(String table, String column) {
		String sql = "select string_agg(pg_get_constraintdef(c.oid), ' ') from pg_constraint c "
				+ "join pg_class t on t.oid = c.conrelid "
				+ "join pg_attribute a on a.attrelid = t.oid and a.attnum = any (c.conkey) "
				+ "where c.contype = 'c' and t.relname = ? and a.attname = ? and array_length(c.conkey, 1) = 1";
		String definition = jdbc.queryForObject(sql, String.class, table, column);
		assertThat(definition).as("check on %s.%s", table, column).isNotNull();
		Set<String> words = new TreeSet<>();
		Matcher m = QUOTED.matcher(definition);
		while (m.find()) {
			words.add(m.group(1));
		}
		return words;
	}

	private static Set<String> names(Class<? extends Enum<?>> type) {
		return new TreeSet<>(Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
	}

	private void same(String table, String column, Class<? extends Enum<?>> type) {
		assertThat(names(type)).as("%s vs %s.%s", type.getSimpleName(), table, column)
			.isEqualTo(allowed(table, column));
	}

	@Test
	void enumsMatchTheDatabaseChecks() {
		same("notification", "kind", NotificationKind.class);
		same("notification", "status", NotificationStatus.class);
		same("notification", "source", NotificationSource.class);
		same("notification", "media_type", MediaFileType.class);
		same("message_template", "kind", TemplateKind.class);
		same("message_template", "header_type", HeaderType.class);
		same("message_template_button", "type", ButtonType.class);
		same("message_stop", "reason", StopReason.class);
		same("message_outbox", "purpose", MessagePurpose.class);
		same("message_outbox", "status", MessageStatus.class);
		same("message_outbox", "button_action", ButtonAction.class);
		same("inbound_message", "button_action", ButtonAction.class);
	}

	@Test
	void everyNotificationKindHasATemplateKind() {
		for (NotificationKind kind : NotificationKind.values()) {
			assertThat(TemplateKind.of(kind).name()).isEqualTo(kind.name());
		}
	}

}
