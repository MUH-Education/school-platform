package com.muhjain.school.student;

import java.util.List;

import com.muhjain.school.auth.CurrentUser;
import java.io.IOException;

import com.muhjain.school.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Students screens. Reading needs STUDENTS_VIEW, changing needs STUDENTS_EDIT.
 * Students are never deleted: a child who leaves gets status LEFT.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

	private final StudentService studentService;

	private final GuardianService guardianService;

	private final TransportEnrolmentService transportService;

	private final StudentPhotoService photoService;

	private final CurrentUser currentUser;

	public StudentController(StudentService studentService, GuardianService guardianService,
			TransportEnrolmentService transportService, StudentPhotoService photoService, CurrentUser currentUser) {
		this.photoService = photoService;
		this.studentService = studentService;
		this.guardianService = guardianService;
		this.transportService = transportService;
		this.currentUser = currentUser;
	}

	/**
	 * The Students list, paged: {@code ?page=0&size=25&sort=name,asc}. Filters: {@code q} (name, admission number or
	 * phone), {@code className}, {@code village}, {@code routeId}, {@code bus=YES|NO}. Only active students, unless
	 * {@code status=LEFT}.
	 */
	@GetMapping
	@PreAuthorize("hasAuthority('STUDENTS_VIEW')")
	public PageResponse<StudentListItem> list(@RequestParam(required = false) String q,
			@RequestParam(required = false) String className, @RequestParam(required = false) String village,
			@RequestParam(required = false) Long routeId, @RequestParam(required = false) BusFilter bus,
			@RequestParam(required = false) StudentStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String sort) {
		return studentService.list(new StudentFilter(q, className, village, routeId, bus, status), page, size, sort);
	}

	/** The full profile: details, parents, bus now, photo flag. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('STUDENTS_VIEW')")
	public StudentResponse get(@PathVariable Long id) {
		return studentService.get(id);
	}

	/** The change history, newest first: what changed, who did it, when. */
	@GetMapping("/{id}/history")
	@PreAuthorize("hasAuthority('STUDENTS_VIEW')")
	public List<HistoryItem> history(@PathVariable Long id) {
		return studentService.history(id);
	}

	/**
	 * A child leaves (status LEFT) or comes back (status ACTIVE). Students are never deleted. Leaving closes the
	 * open bus row.
	 */
	@PutMapping("/{id}/status")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	public StudentResponse setStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
		return studentService.setStatus(id, request);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	public StudentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
		return studentService.update(id, request);
	}

	/** Add a phone number. A number that other children already use is reused, not copied. */
	@PostMapping("/{id}/guardians")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	@ResponseStatus(HttpStatus.CREATED)
	public GuardianResponse addGuardian(@PathVariable Long id, @Valid @RequestBody GuardianRequest request) {
		return guardianService.add(id, request);
	}

	@PutMapping("/{id}/guardians/{guardianId}")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	public GuardianResponse updateGuardian(@PathVariable Long id, @PathVariable Long guardianId,
			@Valid @RequestBody UpdateGuardianRequest request) {
		return guardianService.update(id, guardianId, request);
	}

	/** Removes the link, not the phone. 409 LAST_GUARDIAN if it is the last phone of the child. */
	@DeleteMapping("/{id}/guardians/{guardianId}")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeGuardian(@PathVariable Long id, @PathVariable Long guardianId) {
		guardianService.remove(id, guardianId);
	}

	/** The bus history of the child, newest first. */
	@GetMapping("/{id}/transport")
	@PreAuthorize("hasAuthority('STUDENTS_VIEW')")
	public List<EnrolmentResponse> transport(@PathVariable Long id) {
		return transportService.history(id);
	}

	/**
	 * Start the bus, change route or stop, or stop the bus. The answer has a {@code warning} when the route is over
	 * its seats. The child is saved anyway: the school decides, the software only warns.
	 */
	@PutMapping("/{id}/transport")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	public TransportSaveResponse saveTransport(@PathVariable Long id, @Valid @RequestBody TransportRequest request) {
		return transportService.save(id, request, currentUser.id());
	}

	/** Upload a photo: multipart field {@code file}, JPEG or PNG, at most 2 MB. A new photo replaces the old one. */
	@PostMapping("/{id}/photo")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	public PhotoResponse uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file)
			throws IOException {
		return photoService.upload(id, file.getBytes());
	}

	/** The photo bytes. There is no public URL: the permission is checked here, on every request. */
	@GetMapping("/{id}/photo")
	@PreAuthorize("hasAuthority('STUDENTS_VIEW')")
	public ResponseEntity<byte[]> photo(@PathVariable Long id) {
		PhotoContent photo = photoService.open(id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(photo.contentType()))
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(photo.bytes());
	}

	@DeleteMapping("/{id}/photo")
	@PreAuthorize("hasAuthority('STUDENTS_EDIT')")
	public PhotoResponse removePhoto(@PathVariable Long id) {
		return photoService.remove(id);
	}

}
