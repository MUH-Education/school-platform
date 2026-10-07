package com.muhjain.school.student;

import java.util.List;

/**
 * What the import did (rule 25). Lines with errors are skipped, good lines are saved.
 * With {@code dryRun = true} nothing is saved and {@code created} is the number of lines that <b>would</b> be saved.
 * Example: {@code { "dryRun": false, "created": 284, "errors": [ { "line": 17, "message": "Phone has 9 digits" } ] }}
 */
public record ImportResponse(boolean dryRun, int created, List<ImportError> errors) {

}
