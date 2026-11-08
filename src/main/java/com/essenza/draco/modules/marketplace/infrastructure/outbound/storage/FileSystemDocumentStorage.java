package com.essenza.draco.modules.marketplace.infrastructure.outbound.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.marketplace.application.port.DocumentStorage;
import com.essenza.draco.shared.exceptions.NotFoundException;

/**
 * Documentos de vendedores en disco, FUERA de cualquier carpeta servida por la web
 * ({@code essenza.marketplace.documents-dir}, por defecto {@code ~/.essenza/draco/seller-documents}).
 * Una carpeta por empresa; el nombre del archivo es un UUID sin extensión ni datos del vendedor.
 * Para producción se puede reemplazar por un bucket privado implementando {@link DocumentStorage}.
 */
@Component
public class FileSystemDocumentStorage implements DocumentStorage {

    private final Path root;

    public FileSystemDocumentStorage(
            @Value("${essenza.marketplace.documents-dir:${user.home}/.essenza/draco/seller-documents}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String put(long companyId, byte[] content) {
        String key = UUID.randomUUID().toString();
        Path file = resolve(companyId, key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo guardar el documento", ex);
        }
        return key;
    }

    @Override
    public InputStream open(long companyId, String key) {
        Path file = resolve(companyId, key);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("El archivo del documento no está disponible.");
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el documento", ex);
        }
    }

    /** La clave debe ser un UUID: impide rutas como "../" aunque alguien manipule la base. */
    Path resolve(long companyId, String key) {
        UUID uuid = UUID.fromString(key);
        Path file = root.resolve(Long.toString(companyId)).resolve(uuid.toString()).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalStateException("Ruta de documento inválida");
        }
        return file;
    }
}
