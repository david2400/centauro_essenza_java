package com.essenza.draco.modules.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.infrastructure.outbound.storage.FileSystemDocumentStorage;

/** Validación de archivos de vendedores: tipo real, nombre seguro y almacenamiento privado. */
class SellerDocumentRulesTest {

    @Test
    void detectsTypeByContentNotByExtension() {
        assertThat(SellerDocumentService.detectType("%PDF-1.7".getBytes())).isEqualTo("application/pdf");
        assertThat(SellerDocumentService.detectType(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})).isEqualTo("image/png");
        assertThat(SellerDocumentService.detectType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
        assertThat(SellerDocumentService.detectType("<html><script>".getBytes())).isNull();
        assertThat(SellerDocumentService.detectType("MZ".getBytes())).isNull();
    }

    @Test
    void safeNameDropsPathsAndUsesRealExtension() {
        assertThat(SellerDocumentService.safeName("C:\\fakepath\\..\\RUT 2026.exe", "application/pdf")).isEqualTo("RUT 2026.pdf");
        assertThat(SellerDocumentService.safeName("../../etc/passwd", "image/png")).isEqualTo("passwd.png");
        assertThat(SellerDocumentService.safeName("\"<>|.pdf", "application/pdf")).isEqualTo("documento.pdf");
        assertThat(SellerDocumentService.safeName(null, "image/jpeg")).isEqualTo("documento.jpg");
        assertThat(SellerDocumentService.sha256("abc".getBytes()))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void storageKeysAreOpaqueAndCannotEscapeTheFolder() throws Exception {
        Path dir = Files.createTempDirectory("mp-docs");
        FileSystemDocumentStorage storage = new FileSystemDocumentStorage(dir.toString());
        String key = storage.put(7, "%PDF-x".getBytes());
        assertThat(key).matches("[0-9a-f-]{36}");
        assertThat(Files.exists(dir.resolve("7").resolve(key))).isTrue();
        try (var in = storage.open(7, key)) {
            assertThat(new String(in.readAllBytes())).isEqualTo("%PDF-x");
        }
        assertThatThrownBy(() -> storage.open(7, "../../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.open(8, key)).hasMessageContaining("no está disponible");
    }
}
