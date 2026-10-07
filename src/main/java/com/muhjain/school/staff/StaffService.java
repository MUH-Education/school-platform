package com.muhjain.school.staff;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.muhjain.school.common.ApiException;
import com.muhjain.school.common.NameKeys;
import com.muhjain.school.common.PhoneNumbers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drivers, attendants and helpers. Rules 4 and 5 of docs/phases/phase-2-vehicles-staff-routes.md.
 * Other features use this service, never {@link StaffRepository}.
 */
@Service
public class StaffService {

	private final StaffRepository staff;

	private final Clock clock;

	public StaffService(StaffRepository staff, Clock clock) {
		this.staff = staff;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<StaffResponse> list() {
		LocalDate today = today();
		return staff.findAllByOrderByIdAsc().stream().map(s -> StaffResponse.of(s, today)).toList();
	}

	/** @throws ApiException 404 NOT_FOUND */
	@Transactional(readOnly = true)
	public StaffResponse get(Long id) {
		return StaffResponse.of(find(id), today());
	}

	/**
	 * Rule 4. A DRIVER needs a licence number and an end date. For others both are ignored.
	 *
	 * @throws ApiException 400 VALIDATION for a bad phone, or a driver with no licence
	 */
	@Transactional
	public StaffResponse create(CreateStaffRequest request) {
		Staff person = new Staff(NameKeys.tidy(request.name()), PhoneNumbers.normalize(request.phone()),
				request.staffType());
		applyLicence(person, request.licenceNo(), request.licenceValidTill());
		return StaffResponse.of(staff.saveAndFlush(person), today());
	}

	/** @throws ApiException 404 NOT_FOUND, 400 VALIDATION */
	@Transactional
	public StaffResponse update(Long id, UpdateStaffRequest request) {
		Staff person = find(id);
		person.setName(NameKeys.tidy(request.name()));
		person.setPhone(PhoneNumbers.normalize(request.phone()));
		person.setStaffType(request.staffType());
		applyLicence(person, request.licenceNo(), request.licenceValidTill());
		person.setActive(request.active());
		return StaffResponse.of(staff.saveAndFlush(person), today());
	}

	/** Turn off. People are never deleted (B10). Turning off a person who is already off changes nothing. */
	@Transactional
	public void turnOff(Long id) {
		find(id).setActive(false);
	}

	/** The school day, in the school zone. */
	LocalDate today() {
		return LocalDate.now(clock);
	}

	private Staff find(Long id) {
		return staff.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This person does not exist."));
	}

	// Rule 4: only a DRIVER has a licence. For everyone else the two fields are cleared.
	private static void applyLicence(Staff person, String licenceNo, LocalDate licenceValidTill) {
		if (person.getStaffType() != StaffType.DRIVER) {
			person.setLicenceNo(null);
			person.setLicenceValidTill(null);
			return;
		}
		if (licenceNo == null || licenceNo.isBlank()) {
			throw ApiException.validation("licenceNo", "is required for a driver");
		}
		if (licenceValidTill == null) {
			throw ApiException.validation("licenceValidTill", "is required for a driver");
		}
		person.setLicenceNo(NameKeys.tidy(licenceNo));
		person.setLicenceValidTill(licenceValidTill);
	}

}
