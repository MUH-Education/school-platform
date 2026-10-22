package com.muhjain.school.model.dto.response;

/**
 * The answer to the attendant's "running late" button.
 * Example: route 4, 20 minutes, {@code willNotify true}: the families on Route 4 get a message within a minute.
 *
 * @param willNotify the BUS_LATE rule is on and this route has not been reported late yet this morning (or evening)
 * @param alreadySent the route was reported late already this morning (or evening): nothing new is sent
 */
public record RunningLateResponse(Long routeId, int minutes, boolean willNotify, boolean alreadySent) {

}
