package com.muhjain.school.enquiry;

import com.muhjain.school.auth.CurrentUser;
import com.muhjain.school.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Enquiry screens. Reading needs ENQUIRIES_VIEW, changing needs ENQUIRIES_EDIT.
 * Enquiries are never deleted: a lost one is moved to LOST.
 */
@RestController
@RequestMapping("/api/v1/enquiries")
public class EnquiryController {

	private final EnquiryService enquiryService;

	private final CurrentUser currentUser;

	public EnquiryController(EnquiryService enquiryService, CurrentUser currentUser) {
		this.enquiryService = enquiryService;
		this.currentUser = currentUser;
	}

	/**
	 * Paged list, newest first. Filters: {@code status}, {@code village}, {@code source}, {@code overdue=true},
	 * {@code q} (name or phone).
	 */
	@GetMapping
	@PreAuthorize("hasAuthority('ENQUIRIES_VIEW')")
	public PageResponse<EnquiryResponse> list(@RequestParam(required = false) EnquiryStatus status,
			@RequestParam(required = false) String village, @RequestParam(required = false) EnquirySource source,
			@RequestParam(defaultValue = "false") boolean overdue, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
		return enquiryService.list(status, village, source, overdue, q, page, size);
	}

	/** Count per stage, overdue, admitted percent and count per village. */
	@GetMapping("/summary")
	@PreAuthorize("hasAuthority('ENQUIRIES_VIEW')")
	public EnquirySummaryResponse summary() {
		return enquiryService.summary();
	}

	/** Add an enquiry. 409 ENQUIRY_EXISTS (with {@code fields.enquiryId}) if the phone and class are open already. */
	@PostMapping
	@PreAuthorize("hasAuthority('ENQUIRIES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public EnquiryResponse create(@Valid @RequestBody EnquiryRequest request) {
		return enquiryService.create(request, currentUser.id());
	}

	/** One enquiry with its follow-ups, newest first. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('ENQUIRIES_VIEW')")
	public EnquiryResponse get(@PathVariable Long id) {
		return enquiryService.get(id);
	}

	/** Change the details. The whole object is sent again. The stage changes only with {@code /status}. */
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ENQUIRIES_EDIT')")
	public EnquiryResponse update(@PathVariable Long id, @Valid @RequestBody EnquiryRequest request) {
		return enquiryService.update(id, request);
	}

	/** The fields the New admission screen can copy from this enquiry. Needs ADMISSIONS_CREATE. */
	@GetMapping("/{id}/prefill")
	@PreAuthorize("hasAuthority('ADMISSIONS_CREATE')")
	public PrefillResponse prefill(@PathVariable Long id) {
		return enquiryService.prefill(id);
	}

	/** Move to another stage. LOST needs {@code lostReason}. ADMITTED cannot be set here. */
	@PostMapping("/{id}/status")
	@PreAuthorize("hasAuthority('ENQUIRIES_EDIT')")
	public EnquiryResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
		return enquiryService.changeStatus(id, request);
	}

	/** Add a call or visit note. It may set the next follow-up date. Answers the enquiry with its follow-ups. */
	@PostMapping("/{id}/follow-ups")
	@PreAuthorize("hasAuthority('ENQUIRIES_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public EnquiryResponse addFollowUp(@PathVariable Long id, @Valid @RequestBody FollowUpRequest request) {
		return enquiryService.addFollowUp(id, request, currentUser.id());
	}

}
