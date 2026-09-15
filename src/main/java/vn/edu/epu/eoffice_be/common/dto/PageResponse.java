package vn.edu.epu.eoffice_be.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO phân trang kết quả trả về chuẩn hóa")
public class PageResponse<T> {

	@Schema(description = "Danh sách dữ liệu trong trang hiện tại")
	private List<T> content;

	@Schema(description = "Số thứ tự trang hiện tại (bắt đầu từ 1)", example = "1")
	private int page;

	@Schema(description = "Số phần tử trên mỗi trang", example = "10")
	private int size;

	@Schema(description = "Tổng số phần tử trên tất cả các trang", example = "42")
	private long totalElements;

	@Schema(description = "Tổng số trang", example = "5")
	private int totalPages;

	@Schema(description = "Là trang đầu tiên", example = "true")
	private boolean first;

	@Schema(description = "Là trang cuối cùng", example = "false")
	private boolean last;

	public static <T> PageResponse<T> from(Page<T> page) {
		if (page == null) {
			return PageResponse.<T>builder()
					.content(List.of())
					.page(1)
					.size(0)
					.totalElements(0)
					.totalPages(0)
					.first(true)
					.last(true)
					.build();
		}
		return PageResponse.<T>builder()
				.content(page.getContent())
				.page(page.getNumber() + 1)
				.size(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.first(page.isFirst())
				.last(page.isLast())
				.build();
	}
}
