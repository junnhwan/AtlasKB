package io.hwan.atlaskb.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    @Test
    void businessExceptionReturnsUnifiedResponse() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.message").value("boom"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void unexpectedExceptionReturnsUnifiedResponse() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(5000))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @RestController
    public static class FailingController {

        @GetMapping("/test/business-error")
        String businessError() {
            throw new BusinessException(4000, "boom");
        }

        @GetMapping("/test/unexpected-error")
        String unexpectedError() {
            throw new IllegalStateException("unexpected");
        }
    }
}
