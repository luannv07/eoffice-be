package vn.edu.epu.eoffice_be.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequest {

    @NotBlank(message = "username không được để trống")
    @Size(max = 100, message = "username tối đa 100 ký tự")
    String username;

    @NotBlank(message = "email không được để trống")
    @Email(message = "email không hợp lệ")
    @Size(max = 255, message = "email tối đa 255 ký tự")
    String email;

    @NotBlank(message = "password không được để trống")
    @Size(min = 8, message = "password tối thiểu 8 ký tự")
    @Size(max = 72, message = "password tối đa 72 ký tự")
    String password;
}
