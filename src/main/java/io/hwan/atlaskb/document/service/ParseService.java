package io.hwan.atlaskb.document.service;

import io.hwan.atlaskb.document.model.TextChunk;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

@Service
public class ParseService {

    private final ChunkingService chunkingService;

    public ParseService(ChunkingService chunkingService) {
        this.chunkingService = chunkingService;
    }

    public List<TextChunk> parse(InputStream inputStream, String fileName) throws IOException, TikaException {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

        try {
            new AutoDetectParser().parse(inputStream, handler, metadata, new ParseContext());
        } catch (SAXException exception) {
            throw new TikaException("Failed to parse document", exception);
        }

        return chunkingService.chunk(handler.toString());
    }
}
