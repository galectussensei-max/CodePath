package com.codepath.revision;

import java.util.List;

/** Response of {@code GET /api/revision/queue}, most urgent topic first. */
public record RevisionQueueDto(int dueCount, List<RevisionItemDto> items, String note) {
}
