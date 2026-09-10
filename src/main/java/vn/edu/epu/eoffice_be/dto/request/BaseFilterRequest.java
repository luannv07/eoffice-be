package vn.edu.epu.eoffice_be.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.Range;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

@Data
@Schema(description = "Phân trang + sắp xếp chung cho các endpoint tra cứu danh sách (dạng query param)")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BaseFilterRequest {
	private static final String CREATED_DATE = "createdDate";
	private static final String UPDATED_DATE = "updatedDate";

	@Schema(description = "Số trang (bắt đầu từ 1)", example = "1")
	@Range(min = 1, max = 1000, message = "{validation.field.size_not_in_range}")
	Integer page = 1;

	@Schema(description = "Số bản ghi mỗi trang", example = "20")
	@Range(min = 1, max = 1000, message = "{validation.field.size_not_in_range}")
	Integer size = 10;

	@Schema(description = "Trường sắp xếp. Mỗi endpoint có danh sách trường cho phép riêng, "
					+ "trường không hợp lệ sẽ tự fallback về mặc định của endpoint đó", example = "createdDate")
	String sortBy = CREATED_DATE;

	@Schema(description = "Chiều sắp xếp: ASC hoặc DESC", example = "DESC")
	Sort.Direction sortDirection = Sort.Direction.DESC;

	public Pageable pageable() {
		String validatedSortBy = getAllowedSortFields().contains(sortBy)
						? sortBy
						: getDefaultSortBy();

		return PageRequest.of(
						page - 1,
						size,
						Sort.by(sortDirection, validatedSortBy)
		);
	}

	protected Set<String> getAllowedSortFields() {
		return Set.of(CREATED_DATE, UPDATED_DATE);
	}

	protected String getDefaultSortBy() {
		return CREATED_DATE;
	}
}
