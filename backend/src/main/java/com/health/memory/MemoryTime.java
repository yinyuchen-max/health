package com.health.memory;

import java.time.*;
import java.util.regex.Pattern;

public final class MemoryTime {
    private MemoryTime() {}
    /** Preserve unknown event time; never silently equate a reported past event with ingestion time. */
    public static LocalDateTime resolve(String proposed,String evidence,LocalDateTime recorded) {
        if(proposed==null||evidence==null) return null;
        try {
            LocalDate day=null;
            var match=Pattern.compile("(20\\d{2})[-年](\\d{1,2})[-月](\\d{1,2})日?").matcher(evidence);
            if(match.find()) day=LocalDate.of(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),Integer.parseInt(match.group(3)));
            else if(evidence.contains("前天")) day=recorded.toLocalDate().minusDays(2);
            else if(evidence.contains("昨天")) day=recorded.toLocalDate().minusDays(1);
            else if(evidence.contains("今天")) day=recorded.toLocalDate();
            LocalDate proposedDay=LocalDate.parse(proposed.substring(0,10));
            return proposedDay.equals(day)?day.atStartOfDay():null;
        } catch(Exception ignored) { return null; }
    }
}
