package com.muhjain.school.student;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.muhjain.school.fee.AdmissionFeeRequest;
import com.muhjain.school.fee.PaymentRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new admission: the child, the parents' phones and the bus, saved together.
 * <ul>
 * <li>{@code guardians}: at least one phone, unless {@code siblingStudentId} is given.</li>
 * <li>{@code siblingStudentId}: a brother or sister already in this school. The new child gets every phone of
 * that child, so the clerk does not type the parents again.</li>
 * <li>{@code enquiryId}: the enquiry this admission comes from. It becomes ADMITTED with this child, in the same
 * transaction (400 if it does not exist, 409 ENQUIRY_ALREADY_ADMITTED if a child was admitted from it already).</li>
 * <li>{@code joinedOn}: optional, means today.</li>
 * <li>{@code fee}: the fee plan, optional (no plan, no dues). {@code firstPayment}: optional money received at the
 * desk, needs {@code fee}. Both are saved in the same transaction as the child.</li>
 * </ul>
 * Example: {@code { "name": "Aryan", "dob": "2018-05-14", "gender": "M", "className": "3", "section": "B",
 * "village": "Jakhal", "fatherOccupation": "FARMER_SMALL",
 * "guardians": [ { "name": "Ramesh", "phone": "98123 40208", "relation": "FATHER" } ],
 * "bus": { "routeId": 4, "stopId": 18, "busFee": 8800 },
 * "fee": { "schoolFee": 30000, "busFee": 8800, "payFrequency": "QUARTERLY" },
 * "firstPayment": { "mode": "UPI", "lines": [ { "feeHead": "SCHOOL", "amount": 7500 }, { "feeHead": "BUS", "amount": 2200 } ] } }}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(example = "{\"name\": \"Aryan\", \"dob\": \"2018-05-14\", \"gender\": \"M\", \"className\": \"3\", \"section\": \"B\", \"village\": \"Jakhal\", \"fatherOccupation\": \"FARMER_SMALL\", \"guardians\": [{\"name\": \"Ramesh\", \"phone\": \"98123 40208\", \"relation\": \"FATHER\"}], \"bus\": {\"routeId\": 4, \"stopId\": 18, \"busFee\": 8800}, \"fee\": {\"schoolFee\": 30000, \"busFee\": 8800, \"payFrequency\": \"QUARTERLY\"}, \"firstPayment\": {\"mode\": \"UPI\", \"lines\": [{\"feeHead\": \"SCHOOL\", \"amount\": 7500}, {\"feeHead\": \"BUS\", \"amount\": 2200}]}}")
public record AdmissionRequest(@NotBlank @Size(max = 120) String name, @NotNull LocalDate dob, @NotNull Gender gender,
		@NotBlank String className, @Size(max = 4) String section, @NotBlank @Size(max = 80) String village,
		@Size(max = 200) String address, @NotNull FatherOccupation fatherOccupation, LocalDate joinedOn,
		@Valid List<@NotNull @Valid GuardianRequest> guardians, Long siblingStudentId, Long enquiryId,
		@Valid AdmissionBus bus, @Valid AdmissionFeeRequest fee, @Valid PaymentRequest firstPayment) {

}
