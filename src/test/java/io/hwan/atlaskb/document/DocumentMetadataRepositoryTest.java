package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.hwan.atlaskb.document.entity.ChunkInfo;
import io.hwan.atlaskb.document.entity.DocumentVector;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.ChunkInfoRepository;
import io.hwan.atlaskb.document.repository.DocumentVectorRepository;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DocumentMetadataRepositoryTest {

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private ChunkInfoRepository chunkInfoRepository;

    @Autowired
    private DocumentVectorRepository documentVectorRepository;

    @Test
    void savesDocumentMetadata() {
        FileUpload fileUpload = new FileUpload();
        fileUpload.setFileMd5("abc123");
        fileUpload.setFileName("manual.pdf");
        fileUpload.setTotalSize(1024L);
        fileUpload.setStatus(1);
        fileUpload.setUserId("42");
        fileUpload.setOrgTag("default");
        fileUpload.setPublic(false);
        fileUploadRepository.save(fileUpload);

        ChunkInfo chunkInfo = new ChunkInfo();
        chunkInfo.setFileMd5("abc123");
        chunkInfo.setChunkIndex(0);
        chunkInfo.setChunkMd5("chunk123");
        chunkInfo.setStoragePath("chunks/abc123/0");
        chunkInfoRepository.save(chunkInfo);

        DocumentVector documentVector = new DocumentVector();
        documentVector.setFileMd5("abc123");
        documentVector.setChunkId(0);
        documentVector.setTextContent("AtlasKB test");
        documentVector.setModelVersion("v1");
        documentVector.setUserId("42");
        documentVector.setOrgTag("default");
        documentVector.setPublic(false);
        documentVectorRepository.save(documentVector);

        assertEquals(1L, fileUploadRepository.count());
        assertEquals(1L, chunkInfoRepository.count());
        assertEquals(1L, documentVectorRepository.count());
    }
}
