package io.hwan.atlaskb.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.hwan.atlaskb.common.logging.RequestLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingInterceptorTest {

    @Test
    void preHandleStoresStartTimeAndAllowsRequest() throws Exception {
        RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertNotNull(request.getAttribute(RequestLoggingInterceptor.REQUEST_START_TIME_ATTRIBUTE));
        assertDoesNotThrow(() -> interceptor.afterCompletion(request, response, new Object(), null));
    }
}
