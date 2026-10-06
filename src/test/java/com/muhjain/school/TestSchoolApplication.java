package com.muhjain.school;

import org.springframework.boot.SpringApplication;

public class TestSchoolApplication {

	public static void main(String[] args) {
		SpringApplication.from(SchoolApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
