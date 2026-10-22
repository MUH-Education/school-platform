package com.muhjain.school.model.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** One date of the FESTIVAL rule and its name for {@code {Festival}}. Example: 2027-03-04, "होली". */
public record FestivalDay(@NotNull LocalDate date, @NotBlank @Size(max = 40) String name) {

}
