package vn.edu.epu.eoffice_be.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VersionCompareResponse {

    int fromVersionNumber;
    int toVersionNumber;

    boolean titleChanged;
    String oldTitle;
    String newTitle;

    boolean summaryChanged;
    String oldSummary;
    String newSummary;

    boolean contentChanged;
    String oldContent;
    String newContent;
}
