package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.hwan.atlaskb.document.model.TextChunk;
import io.hwan.atlaskb.document.service.ChunkingService;
import io.hwan.atlaskb.document.service.ParseService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParseServiceTest {

    @Test
    void parsesPlainTextStreamIntoChunks() throws Exception {
        ParseService parseService = new ParseService(new ChunkingService(50, 10));

        List<TextChunk> chunks = parseService.parse(
                new ByteArrayInputStream("AtlasKB handles RAG documents.".getBytes(StandardCharsets.UTF_8)),
                "notes.txt"
        );

        assertEquals(1, chunks.size());
        assertEquals(new TextChunk(1, "AtlasKB handles RAG documents."), chunks.get(0));
    }

    @Test
    void returnsEmptyWhenParsedDocumentIsBlank() throws Exception {
        ParseService parseService = new ParseService(new ChunkingService(50, 10));

        List<TextChunk> chunks = parseService.parse(
                new ByteArrayInputStream("   \n\t ".getBytes(StandardCharsets.UTF_8)),
                "blank.txt"
        );

        assertTrue(chunks.isEmpty());
    }
}
