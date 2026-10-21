package com.muhjain.school.model.enums;

/**
 * A group of people a notification can go to. Each group has one {@code RecipientResolver}.
 * Example: CURRENT_PARENTS with filter classes ["10"] = the families of Class 10, one message per family.
 * (There is no OFFICE group: no staff type for office people exists yet, docs/08-decisions.md.)
 */
public enum AudienceGroup {

	CURRENT_PARENTS, ROUTE, ENQUIRY_OPEN, ENQUIRY_LOST, FAMILIES_WITH_DUES,
	STAFF_ALL, DRIVERS, ATTENDANTS, TEACHERS, ROUTE_CREW,
	ONE_FAMILY, ONE_STAFF

}
