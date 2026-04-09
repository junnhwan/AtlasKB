package io.hwan.atlaskb.storage.service;

import java.io.IOException;
import java.io.InputStream;

public interface StorageObjectReader {

    InputStream read(String objectUrl) throws IOException;
}
