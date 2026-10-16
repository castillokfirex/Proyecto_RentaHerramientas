package com.rentatools.shared.application.port.out;

import java.io.InputStream;

/**
 * Puerto de salida para almacenamiento de archivos binarios (imágenes de herramientas, fotos de inspección).
 */
public interface FileStorage {

    StoredFile store(String folder, String originalFilename, String contentType, InputStream content, long size);

    InputStream open(String storageKey);

    void delete(String storageKey);
}
