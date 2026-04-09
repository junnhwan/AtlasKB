package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hwan.atlaskb.document.model.FileProcessingTask;
import org.junit.jupiter.api.Test;

class FileProcessingTaskSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesAndDeserializesTaskPayload() throws Exception {
        FileProcessingTask task = new FileProcessingTask(
                "abc123",
                "http://localhost:9000/atlas-kb-uploads/merged/manual.pdf",
                "manual.pdf",
                "1",
                "default",
                true
        );

        String json = objectMapper.writeValueAsString(task);

        assertTrue(json.contains("\"fileMd5\":\"abc123\""));
        assertTrue(json.contains("\"objectUrl\":\"http://localhost:9000/atlas-kb-uploads/merged/manual.pdf\""));
        assertTrue(json.contains("\"fileName\":\"manual.pdf\""));
        assertTrue(json.contains("\"userId\":\"1\""));
        assertTrue(json.contains("\"orgTag\":\"default\""));
        assertTrue(json.contains("\"publicAccessible\":true"));

        FileProcessingTask restored = objectMapper.readValue(json, FileProcessingTask.class);

        assertEquals("abc123", restored.fileMd5());
        assertEquals("http://localhost:9000/atlas-kb-uploads/merged/manual.pdf", restored.objectUrl());
        assertEquals("manual.pdf", restored.fileName());
        assertEquals("1", restored.userId());
        assertEquals("default", restored.orgTag());
        assertTrue(restored.publicAccessible());
    }

    @Test
    void deserializesFalseVisibilityFlag() throws Exception {
        String json = """
                {
                  "fileMd5": "abc123",
                  "objectUrl": "http://localhost:9000/atlas-kb-uploads/merged/manual.pdf",
                  "fileName": "manual.pdf",
                  "userId": "1",
                  "orgTag": "default",
                  "publicAccessible": false
                }
                """;

        FileProcessingTask restored = objectMapper.readValue(json, FileProcessingTask.class);

        assertFalse(restored.publicAccessible());
    }
}
