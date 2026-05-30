package vn.edu.epu.eoffice_be.audit.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.epu.eoffice_be.dto.request.BaseFilterRequest;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeHistoryLogFilterRequest extends BaseFilterRequest {

    @Size(max = 100, message = "tableName tối đa 100 ký tự")
    String tableName;

    @Size(max = 100, message = "objectId tối đa 100 ký tự")
    String objectId;

    @Size(max = 100, message = "changedBy tối đa 100 ký tự")
    String changedBy;

    @Size(max = 20, message = "action tối đa 20 ký tự")
    String action;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant changedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant changedTo;

    public ChangeHistoryLogFilterRequest() {
        setSortBy("changedAt");
        setSortDirection(Sort.Direction.DESC);
    }

    @Override
    protected Set<String> getAllowedSortFields() {
        return Set.of("changedAt");
    }

    @Override
    protected String getDefaultSortBy() {
        return "changedAt";
    }
}
