package io.hwan.atlaskb.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hwan.atlaskb.auth.service.JwtService;
import io.hwan.atlaskb.storage.dto.MergeRequest;
import io.hwan.atlaskb.storage.model.UploadChunkCommand;
import io.hwan.atlaskb.storage.model.UploadChunkResult;
import io.hwan.atlaskb.storage.model.UploadStatusResult;
import io.hwan.atlaskb.storage.service.UploadService;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import io.hwan.atlaskb.user.service.UserQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        "spring.datasource.url=jdbc:h2:mem:uploaddb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UploadService uploadService;

    @MockBean
    private UserQueryService userQueryService;

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
    void firstChunkRejectsUnsupportedFileType() throws Exception {
        mockMvc.perform(multipart("/api/v1/upload/chunk")
                        .file("file", "malware".getBytes())
                        .param("fileMd5", "abc123")
                        .param("chunkIndex", "0")
                        .param("totalSize", "7")
                        .param("fileName", "virus.exe")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("Unsupported file type"));

        verifyNoInteractions(uploadService);
        verifyNoInteractions(userQueryService);
    }

    @Test
    void uploadChunkFallsBackToPrimaryOrgAndReturnsProgress() throws Exception {
        when(userQueryService.getPrimaryOrg(userId)).thenReturn("default");
        when(uploadService.uploadChunk(any(UploadChunkCommand.class)))
                .thenReturn(new UploadChunkResult(List.of(0), 50.0d));

        mockMvc.perform(multipart("/api/v1/upload/chunk")
                        .file("file", "AtlasKB".getBytes())
                        .param("fileMd5", "abc123")
                        .param("chunkIndex", "0")
                        .param("totalSize", String.valueOf(10 * 1024 * 1024))
                        .param("fileName", "manual.pdf")
                        .param("isPublic", "true")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.uploaded[0]").value(0))
                .andExpect(jsonPath("$.data.progress").value(50.0d));

        ArgumentCaptor<UploadChunkCommand> commandCaptor = ArgumentCaptor.forClass(UploadChunkCommand.class);
        verify(uploadService).uploadChunk(commandCaptor.capture());
        UploadChunkCommand command = commandCaptor.getValue();
        assertEquals("abc123", command.fileMd5());
        assertEquals("default", command.orgTag());
        assertEquals(userId.toString(), command.userId());
        assertEquals(true, command.isPublic());
        assertEquals(0, command.chunkIndex());
        assertEquals("manual.pdf", command.fileName());
        assertEquals(10L * 1024 * 1024, command.totalSize());
        assertEquals("file", command.file().getName());
        assertEquals("AtlasKB".length(), command.file().getSize());

        verify(userQueryService).getPrimaryOrg(userId);
    }

    @Test
    void getUploadStatusReturnsProgressAndFileMetadata() throws Exception {
        when(uploadService.getUploadStatus("abc123", userId.toString()))
                .thenReturn(new UploadStatusResult(List.of(0), 50.0d, "manual.pdf", "pdf"));

        mockMvc.perform(get("/api/v1/upload/status")
                        .param("file_md5", "abc123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.uploaded[0]").value(0))
                .andExpect(jsonPath("$.data.progress").value(50.0d))
                .andExpect(jsonPath("$.data.fileName").value("manual.pdf"))
                .andExpect(jsonPath("$.data.fileType").value("pdf"));

        verify(uploadService).getUploadStatus("abc123", userId.toString());
    }

    @Test
    void mergeChunksReturnsObjectUrl() throws Exception {
        when(uploadService.mergeChunks("abc123", "manual.pdf", userId.toString()))
                .thenReturn("http://localhost:9000/atlas-kb-uploads/merged/manual.pdf");

        mockMvc.perform(post("/api/v1/upload/merge")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileMd5": "abc123",
                                  "fileName": "manual.pdf"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.objectUrl").value("http://localhost:9000/atlas-kb-uploads/merged/manual.pdf"));

        verify(uploadService).mergeChunks("abc123", "manual.pdf", userId.toString());
    }
}
