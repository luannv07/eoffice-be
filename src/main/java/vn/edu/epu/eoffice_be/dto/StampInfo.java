package vn.edu.epu.eoffice_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StampInfo {
    private String signerName;     // Họ tên người ký nháy (ví dụ: Nguyen Van A)
    private String positionTitle;  // Chức danh/Đơn vị (ví dụ: Truong Bo Mon CNTT)
    private String signDate;       // Thời gian ký (ví dụ: 2026-09-10 15:30)
    private String note;           // Ghi chú (ví dụ: Xac nhan tham dinh noi dung)
}
