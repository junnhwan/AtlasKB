package io.hwan.atlaskb;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hwan.atlaskb.common.exception.GlobalExceptionHandler;
import io.hwan.atlaskb.common.logging.RequestLoggingInterceptor;
import io.hwan.atlaskb.common.controller.PingController;
import io.hwan.atlaskb.config.WebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
@Import({WebMvcConfig.class, RequestLoggingInterceptor.class, GlobalExceptionHandler.class})
class AtlasKbApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingEndpointReturnsPong() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }
}
