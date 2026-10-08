package com.muhjain.school.analytics;

/** A file to download. Example: {@code students-2026-10-07.csv}. */
public record CsvFile(String fileName, byte[] content) {

}
