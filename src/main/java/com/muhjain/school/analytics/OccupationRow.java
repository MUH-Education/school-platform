package com.muhjain.school.analytics;

import com.muhjain.school.student.FatherOccupation;

/**
 * One father's occupation in the "payment by occupation" graph. {@code onTime + delayed + defaulted + noPlan = total}.
 * {@code noPlan} counts children without a fee plan that year, so the bars always add up to the number of children.
 * Example: {@code {"occupation":"FARMER_SMALL","onTime":1,"delayed":1,"defaulted":0,"noPlan":0,"total":2}}
 */
public record OccupationRow(FatherOccupation occupation, int onTime, int delayed, int defaulted, int noPlan,
		int total) {

}
