package com.muhjain.school.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build();

	@Test
	void apiExceptionBecomesErrorJson() throws Exception {
		mockMvc.perform(get("/fake/api-exception"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("VEHICLE_IN_USE"))
			.andExpect(jsonPath("$.message").value("This vehicle runs Route 4. Move the route first."))
			.andExpect(jsonPath("$.fields").value(nullValue()));
	}

	@Test
	void invalidBodyBecomesValidationWithFields() throws Exception {
		mockMvc.perform(post("/fake/vehicle").contentType(MediaType.APPLICATION_JSON).content("{\"seats\": 0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("VALIDATION"))
			.andExpect(jsonPath("$.fields.seats").value("must be greater than 0"));
	}

	@Test
	void unexpectedErrorBecomes500WithoutDetails() throws Exception {
		mockMvc.perform(get("/fake/bug"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
			.andExpect(jsonPath("$.message").value("Something went wrong. Please try again."));
	}

	record VehicleRequest(@Positive int seats) {
	}

	@RestController
	static class FakeController {

		@GetMapping("/fake/api-exception")
		void apiException() {
			throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_IN_USE",
					"This vehicle runs Route 4. Move the route first.");
		}

		@PostMapping("/fake/vehicle")
		void vehicle(@Valid @RequestBody VehicleRequest request) {
		}

		@GetMapping("/fake/bug")
		void bug() {
			throw new IllegalStateException("secret internal detail");
		}

	}

}
