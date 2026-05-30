package vn.edu.epu.eoffice_be.audit.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Sort;
import vn.edu.epu.eoffice_be.dto.request.BaseFilterRequest;

import java.time.Instant;
import java.util.Set;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAuditLogFilterRequest extends BaseFilterRequest {

    @Size(max = 255, message = "username tối đa 255 ký tự")
    String username;

    @Size(max = 500, message = "requestUri tối đa 500 ký tự")
    String requestUri;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    Instant createdTo;

    public UserAuditLogFilterRequest() {
        setSortBy("createdDate");
        setSortDirection(Sort.Direction.DESC);
    }

    @Override
    protected Set<String> getAllowedSortFields() {
        return Set.of("createdDate");
    }

    @Override
    protected String getDefaultSortBy() {
        return "createdDate";
    }
}
