package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentUrgencyLevel {
    NORMAL("Thường"),
    URGENT("Khẩn"),
    TOP_URGENT("Hỏa tốc");

    private final String description;
}
