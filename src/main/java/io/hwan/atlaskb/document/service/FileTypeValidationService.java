package io.hwan.atlaskb.document.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FileTypeValidationService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");

    public boolean isAllowed(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return false;
        }

        String extension = fileName.substring(index + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(extension);
    }
}
