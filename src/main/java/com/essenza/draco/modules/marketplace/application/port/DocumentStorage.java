package com.essenza.draco.modules.marketplace.application.port;

import java.io.InputStream;

/**
 * Almacenamiento PRIVADO de archivos de vendedores. La clave es opaca (no contiene el nombre
 * original ni datos del vendedor) y nunca se expone como URL pública: el archivo solo se
 * entrega por el endpoint autorizado de descarga.
 */
public interface DocumentStorage {

    /** Guarda los bytes y devuelve la clave. */
    String put(long companyId, byte[] content);

    InputStream open(long companyId, String key);
}
