package io.hwan.atlaskb.storage.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.springframework.stereotype.Service;

@Service
public class UrlStorageObjectReader implements StorageObjectReader {

    @Override
    public InputStream read(String objectUrl) throws IOException {
        return URI.create(objectUrl).toURL().openStream();
    }
}
