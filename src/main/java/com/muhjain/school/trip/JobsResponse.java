package com.muhjain.school.trip;

/** The four jobs of the attendant and how far each one is. */
public record JobsResponse(JobCount boardedMorning, JobCount reachedSchool, JobCount boardedEvening,
		JobCount reachedHome) {

}
