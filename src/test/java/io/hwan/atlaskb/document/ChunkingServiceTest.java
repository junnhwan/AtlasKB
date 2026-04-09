package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.hwan.atlaskb.document.model.TextChunk;
import io.hwan.atlaskb.document.service.ChunkingService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkingServiceTest {

    @Test
    void splitsTextIntoFixedSizeChunksWithOverlap() {
        ChunkingService chunkingService = new ChunkingService(5, 2);

        List<TextChunk> chunks = chunkingService.chunk("ABCDEFGHIJKLM");

        assertEquals(4, chunks.size());
        assertEquals(new TextChunk(1, "ABCDE"), chunks.get(0));
        assertEquals(new TextChunk(2, "DEFGH"), chunks.get(1));
        assertEquals(new TextChunk(3, "GHIJK"), chunks.get(2));
        assertEquals(new TextChunk(4, "JKLM"), chunks.get(3));
    }

    @Test
    void normalizesWhitespaceAndBomBeforeChunking() {
        ChunkingService chunkingService = new ChunkingService(50, 10);

        List<TextChunk> chunks = chunkingService.chunk("\uFEFFAtlasKB   \n\n  handles\t\tRAG");

        assertEquals(1, chunks.size());
        assertEquals(new TextChunk(1, "AtlasKB handles RAG"), chunks.get(0));
    }

    @Test
    void returnsEmptyWhenTextIsBlankAfterCleaning() {
        ChunkingService chunkingService = new ChunkingService(10, 2);

        List<TextChunk> chunks = chunkingService.chunk("  \n\t  ");

        assertTrue(chunks.isEmpty());
    }
}
