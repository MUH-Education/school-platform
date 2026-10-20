package com.muhjain.school.model.dto.response;

/** A file to download. Example: {@code students-2026-10-07.csv}. */
public record CsvFile(String fileName, byte[] content) {

}
