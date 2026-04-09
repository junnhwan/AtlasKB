package io.hwan.atlaskb.document.service;

import io.hwan.atlaskb.document.model.TextChunk;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChunkingService {

    private final int chunkSize;
    private final int overlapSize;

    public ChunkingService(
            @Value("${file.parsing.chunk-size}") int chunkSize,
            @Value("${file.parsing.overlap-size}") int overlapSize
    ) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlapSize < 0 || overlapSize >= chunkSize) {
            throw new IllegalArgumentException("overlapSize must be between 0 and chunkSize - 1");
        }
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    public List<TextChunk> chunk(String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkId = 1;
        int step = chunkSize - overlapSize;

        while (start < cleaned.length()) {
            int end = Math.min(start + chunkSize, cleaned.length());
            String content = cleaned.substring(start, end).trim();
            if (!content.isBlank()) {
                chunks.add(new TextChunk(chunkId++, content));
            }
            if (end == cleaned.length()) {
                break;
            }
            start += step;
        }

        return chunks;
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\uFEFF", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
