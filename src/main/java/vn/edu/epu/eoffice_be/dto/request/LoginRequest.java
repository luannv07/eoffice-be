package vn.edu.epu.eoffice_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginRequest {

    @NotBlank(message = "username không được để trống")
    @Size(max = 100, message = "username tối đa 100 ký tự")
    String username;

    @NotBlank(message = "password không được để trống")
    @Size(max = 255, message = "password tối đa 255 ký tự")
    String password;
}