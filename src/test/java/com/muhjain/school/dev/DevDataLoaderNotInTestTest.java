package com.muhjain.school.dev;

import com.muhjain.school.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/** The loader must not exist in the {@code test} profile (and so not in {@code prod} either). */
class DevDataLoaderNotInTestTest extends AbstractIntegrationTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private Environment environment;

	@Test
	void noLoaderBeanInTheTestProfile() {
		assertThat(environment.getActiveProfiles()).containsExactly("test");
		assertThat(context.getBeansOfType(DevDataLoader.class)).isEmpty();
	}

	@Test
	void testDatabaseHasNoDevData() {
		assertThat(jdbc.queryForObject("select count(*) from vehicle", Integer.class)).isZero();
	}

}
