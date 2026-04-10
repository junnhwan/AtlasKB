package io.hwan.atlaskb.document;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hwan.atlaskb.auth.service.JwtService;
import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.service.DocumentService;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:documentcontrollerdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.listener.auto-startup=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private DocumentService documentService;

    private String token;
    private Long userId;

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
        userId = savedUser.getId();
        token = jwtService.generateToken(savedUser);
    }

    @Test
    void getUserUploadedFilesReturnsDocumentSummaries() throws Exception {
        when(documentService.getUserUploadedFiles(userId.toString())).thenReturn(List.of(
                new DocumentFileSummary(
                        "abc123",
                        "manual.pdf",
                        1024L,
                        2,
                        userId.toString(),
                        "default",
                        true,
                        "2026-04-10T12:00:00",
                        "2026-04-10T12:05:00"
                )
        ));

        mockMvc.perform(get("/api/v1/documents/uploads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data[0].fileMd5").value("abc123"))
                .andExpect(jsonPath("$.data[0].fileName").value("manual.pdf"))
                .andExpect(jsonPath("$.data[0].totalSize").value(1024))
                .andExpect(jsonPath("$.data[0].status").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].orgTag").value("default"))
                .andExpect(jsonPath("$.data[0].public").value(true))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-04-10T12:00:00"))
                .andExpect(jsonPath("$.data[0].mergedAt").value("2026-04-10T12:05:00"));

        verify(documentService).getUserUploadedFiles(userId.toString());
    }
}
