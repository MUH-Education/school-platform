package com.muhjain.school.analytics;

import java.util.List;

/** Every occupation in the order of the list (also those with 0 children). Example: 10 rows. */
public record PaymentByOccupationResponse(Long sessionId, String sessionName, List<OccupationRow> occupations) {

}
