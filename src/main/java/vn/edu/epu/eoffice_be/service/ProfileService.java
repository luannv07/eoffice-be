package vn.edu.epu.eoffice_be.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.epu.eoffice_be.common.enums.ErrorCode;
import vn.edu.epu.eoffice_be.common.exception.BusinessException;
import vn.edu.epu.eoffice_be.config.JwtProperties;
import vn.edu.epu.eoffice_be.dto.request.CompleteProfileRequest;
import vn.edu.epu.eoffice_be.dto.response.CompleteProfileResponse;
import vn.edu.epu.eoffice_be.dto.response.MeResponse;
import vn.edu.epu.eoffice_be.entity.UserInfo;
import vn.edu.epu.eoffice_be.entity.UserLogin;
import vn.edu.epu.eoffice_be.repository.UserInfoRepository;
import vn.edu.epu.eoffice_be.repository.UserLoginRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileService {

	final UserLoginRepository userLoginRepository;
	final UserInfoRepository userInfoRepository;
	final JwtService jwtService;
	final JwtProperties jwtProperties;

	@Transactional
	public CompleteProfileResponse completeMyProfile(Long userId, CompleteProfileRequest request) {
		UserLogin user = userLoginRepository.findByIdAndDeleteFlgFalse(userId)
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

		String contactEmail = blankToNull(request.getContactEmail());
		if (contactEmail != null) {
			contactEmail = contactEmail.trim();
			userInfoRepository.findByContactEmailAndDeleteFlgFalse(contactEmail).ifPresent(existing -> {
				if (!existing.getUserLogin().getId().equals(user.getId())) {
					throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
				}
			});
		}

		String employeeCode = blankToNull(request.getEmployeeCode());
		if (employeeCode != null) {
			employeeCode = employeeCode.trim();
			userInfoRepository.findByEmployeeCodeAndDeleteFlgFalse(employeeCode).ifPresent(existing -> {
				if (!existing.getUserLogin().getId().equals(user.getId())) {
					throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã nhân viên đã được sử dụng");
				}
			});
		}

		UserInfo info = userInfoRepository.findByUserLoginId(user.getId()).orElse(null);
		if (info == null) {
			info = UserInfo.builder()
							.userLogin(user)
							.build();
		} else if (info.isDeleteFlg()) {
			info.setDeleteFlg(false);
		}

		info.setFullName(request.getFullName().trim());
		info.setDateOfBirth(request.getDateOfBirth());
		info.setGender(blankToNull(request.getGender()));
		info.setNationalIdNumber(blankToNull(request.getNationalIdNumber()));
		info.setAvatarUrl(blankToNull(request.getAvatarUrl()));
		info.setContactEmail(contactEmail);
		info.setContactPhone(blankToNull(request.getContactPhone()));
		info.setAddress(blankToNull(request.getAddress()));
		info.setEmployeeCode(employeeCode);
		info.setAcademicTitle(blankToNull(request.getAcademicTitle()));
		info.setAcademicDegree(blankToNull(request.getAcademicDegree()));
		info.setPartyMembershipDate(request.getPartyMembershipDate());
		info.setEmploymentStatusCd(blankToNull(request.getEmploymentStatusCd()) == null ? "ACTIVE"
						: request.getEmploymentStatusCd().trim());
		info.setJoinOrgDate(request.getJoinOrgDate());

		info = userInfoRepository.save(info);
		log.info("User {} completed profile (user_info_id={})", user.getUsername(), info.getId());

		String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole(), true);

		return CompleteProfileResponse.builder()
						.accessToken(accessToken)
						.expiresIn(jwtProperties.getAccessTokenTtl().toSeconds())
						.profile(toMeResponse(user, info))
						.build();
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private MeResponse toMeResponse(UserLogin user, UserInfo info) {
		MeResponse.MeResponseBuilder builder = MeResponse.builder()
						.id(user.getId())
						.username(user.getUsername())
						.email(user.getEmail())
						.role(user.getRole())
						.userStatusCd(user.getUserStatusCd())
						.mustChangePassword(user.isMustChangePassword())
						.passwordExpiredAt(user.getPasswordExpiredAt())
						.lastLoginAt(user.getLastLoginAt())
						.profileCompleted(info != null && !info.isDeleteFlg());
		if (info != null) {
			builder.fullName(info.getFullName())
							.dateOfBirth(info.getDateOfBirth())
							.gender(info.getGender())
							.nationalIdNumber(info.getNationalIdNumber())
							.avatarUrl(info.getAvatarUrl())
							.contactEmail(info.getContactEmail())
							.contactPhone(info.getContactPhone())
							.address(info.getAddress())
							.employeeCode(info.getEmployeeCode())
							.academicTitle(info.getAcademicTitle())
							.academicDegree(info.getAcademicDegree())
							.partyMembershipDate(info.getPartyMembershipDate())
							.employmentStatusCd(info.getEmploymentStatusCd())
							.joinOrgDate(info.getJoinOrgDate());
		}
		return builder.build();
	}
}
