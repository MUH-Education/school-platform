package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.RuleEvent;

/**
 * Something that happened in the school, sent by {@code RuleEvents} and handled after the transaction that saved it
 * has committed. The fields an event does not use are null.
 * Examples: ADMISSION_SAVED student 129, family 210 · BUS_LATE route 4, 20 minutes · PAYMENT_SAVED student 105,
 * family 214.
 *
 * @param guardianId the family (the child's main parent)
 */
public record SchoolEvent(RuleEvent event, Long studentId, Long guardianId, Long routeId, Integer minutes) {

}
