package com.muhjain.school.model.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.muhjain.school.model.dto.response.ClassFeeItem;

/**
 * The whole list of standard fees of a session. A class that is not in the list has no standard fee.
 * Example: {@code {"fees":[{"className":"Nursery","schoolFee":18000},{"className":"3","schoolFee":30000}]}}
 */
public record ClassFeesRequest(@NotNull @Valid List<ClassFeeItem> fees) {

}
