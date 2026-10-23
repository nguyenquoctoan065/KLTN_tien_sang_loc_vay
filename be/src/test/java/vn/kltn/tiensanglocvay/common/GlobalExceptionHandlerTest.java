package vn.kltn.tiensanglocvay.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void apiExceptionTraDungMaVaThongBao() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy hồ sơ."))
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    @Test
    void apiExceptionCoFields() throws Exception {
        mvc.perform(get("/with-fields"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields.phone").value("Số điện thoại không hợp lệ."));
    }

    @Test
    void loiValidateTraFieldsTheoTenTruong() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."))
                .andExpect(jsonPath("$.fields.name").value("Không được để trống."));
    }

    @Test
    void jsonHongTra400() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ."));
    }

    @Test
    void accessDeniedTra403() throws Exception {
        mvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Không có quyền."));
    }

    @Test
    void loiBatKyTra500KhongLoChiTiet() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Lỗi hệ thống, vui lòng thử lại sau."));
    }

    record Body(@NotBlank(message = "Không được để trống.") String name) {
    }

    @RestController
    static class Probe {
        @GetMapping("/not-found")
        void notFound() {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ.");
        }

        @GetMapping("/with-fields")
        void withFields() {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ.", Map.of("phone", "Số điện thoại không hợp lệ."));
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Body body) {
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("x");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("chi tiết nội bộ");
        }
    }
}
