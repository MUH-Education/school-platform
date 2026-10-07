package com.muhjain.school.student;

import com.muhjain.school.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The New admission screen. Needs ADMISSIONS_CREATE. */
@RestController
@RequestMapping("/api/v1/admissions")
public class AdmissionController {

	private final AdmissionService admissionService;

	private final CurrentUser currentUser;

	public AdmissionController(AdmissionService admissionService, CurrentUser currentUser) {
		this.admissionService = admissionService;
		this.currentUser = currentUser;
	}

	/** Student, parents' phones, bus, fee plan and first payment in one go. */
	@PostMapping
	// The fee plan and the first payment need FEES_EDIT as well. Today every role with ADMISSIONS_CREATE has it.
	@PreAuthorize("hasAuthority('ADMISSIONS_CREATE') and (#request.fee() == null and #request.firstPayment() == null "
			+ "or hasAuthority('FEES_EDIT'))")
	@ResponseStatus(HttpStatus.CREATED)
	public AdmissionResponse admit(@Valid @RequestBody AdmissionRequest request) {
		return admissionService.admit(request, currentUser.id());
	}

}
