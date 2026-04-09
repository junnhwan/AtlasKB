package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.hwan.atlaskb.document.service.FileTypeValidationService;
import org.junit.jupiter.api.Test;

class FileTypeValidationServiceTest {

    private final FileTypeValidationService fileTypeValidationService = new FileTypeValidationService();

    @Test
    void allowsSupportedFileTypes() {
        assertTrue(fileTypeValidationService.isAllowed("handbook.pdf"));
        assertTrue(fileTypeValidationService.isAllowed("notes.DOCX"));
        assertTrue(fileTypeValidationService.isAllowed("readme.md"));
        assertTrue(fileTypeValidationService.isAllowed("draft.txt"));
    }

    @Test
    void rejectsUnsupportedFileTypes() {
        assertFalse(fileTypeValidationService.isAllowed("script.exe"));
        assertFalse(fileTypeValidationService.isAllowed("archive.zip"));
        assertFalse(fileTypeValidationService.isAllowed("no-extension"));
    }
}
