package io.hwan.atlaskb.search;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hwan.atlaskb.auth.service.JwtService;
import io.hwan.atlaskb.search.dto.SearchResult;
import io.hwan.atlaskb.search.service.HybridSearchService;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:searchdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.listener.auto-startup=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private HybridSearchService hybridSearchService;

    private String token;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User user = new User();
        user.setUsername("admin");
        user.setPassword(new BCryptPasswordEncoder().encode("admin123"));
        user.setRole("ADMIN");
        user.setOrgTags("default,admin");
        user.setPrimaryOrg("default");
        User savedUser = userRepository.save(user);
        token = jwtService.generateToken(savedUser);
    }

    @Test
    void searchReturnsPermissionAwareResultsForAuthenticatedUser() throws Exception {
        SearchResult result = new SearchResult();
        result.setFileMd5("abc123");
        result.setChunkId(2);
        result.setTextContent("AtlasKB search result");
        result.setScore(0.91D);
        result.setUserId("1");
        result.setOrgTag("default");
        result.setPublic(true);
        result.setFileName("manual.pdf");

        when(hybridSearchService.search(org.mockito.ArgumentMatchers.any(), eq("1")))
                .thenReturn(List.of(result));

        mockMvc.perform(post("/api/v1/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "atlas",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data[0].fileMd5").value("abc123"))
                .andExpect(jsonPath("$.data[0].chunkId").value(2))
                .andExpect(jsonPath("$.data[0].textContent").value("AtlasKB search result"))
                .andExpect(jsonPath("$.data[0].score").value(0.91D))
                .andExpect(jsonPath("$.data[0].orgTag").value("default"))
                .andExpect(jsonPath("$.data[0].public").value(true))
                .andExpect(jsonPath("$.data[0].fileName").value("manual.pdf"));

        verify(hybridSearchService).search(org.mockito.ArgumentMatchers.any(), eq("1"));
    }

    @Test
    void searchWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "atlas"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4010))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }
}
