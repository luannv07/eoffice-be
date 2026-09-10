package vn.edu.epu.eoffice_be.dto.request;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseFilterRequestTest {

	@Test
	void pageableShouldUseDefaults() {
		BaseFilterRequest req = new BaseFilterRequest();
		Pageable pageable = req.pageable();

		assertEquals(0, pageable.getPageNumber());
		assertEquals(10, pageable.getPageSize());
		assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("createdDate").getDirection());
	}

	@Test
	void pageableShouldFallbackWhenSortByNotAllowed() {
		BaseFilterRequest req = new BaseFilterRequest();
		req.setSortBy("unknown");
		req.setPage(2);
		req.setSize(20);
		req.setSortDirection(Sort.Direction.ASC);

		Pageable pageable = req.pageable();
		assertEquals(1, pageable.getPageNumber());
		assertEquals(20, pageable.getPageSize());
		assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("createdDate").getDirection());
	}

	@Test
	void pageableShouldUseAllowedSortField() {
		BaseFilterRequest req = new BaseFilterRequest();
		req.setSortBy("updatedDate");
		req.setSortDirection(Sort.Direction.ASC);

		Pageable pageable = req.pageable();
		assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("updatedDate").getDirection());
	}

	@Test
	void pageableShouldRespectCustomAllowedSortFieldsFromSubclass() {
		class CustomFilterRequest extends BaseFilterRequest {
			@Override
			protected Set<String> getAllowedSortFields() {
				return Set.of("name");
			}

			@Override
			protected String getDefaultSortBy() {
				return "name";
			}
		}

		CustomFilterRequest req = new CustomFilterRequest();
		req.setSortBy("name");
		Pageable pageable = req.pageable();
		assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("name").getDirection());
	}

	@Test
	void pageableShouldUseCustomDefaultSortFieldWhenSortByInvalid() {
		class CustomFilterRequest extends BaseFilterRequest {
			@Override
			protected Set<String> getAllowedSortFields() {
				return Set.of("name");
			}

			@Override
			protected String getDefaultSortBy() {
				return "name";
			}
		}

		CustomFilterRequest req = new CustomFilterRequest();
		req.setSortBy("createdDate");
		Pageable pageable = req.pageable();
		assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("name").getDirection());
	}
}
