package vn.edu.epu.eoffice_be.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentSecurityLevel {
    NORMAL("Thường"),
    CONFIDENTIAL("Mật"),
    SECRET("Tuyệt mật");

    private final String description;
}
