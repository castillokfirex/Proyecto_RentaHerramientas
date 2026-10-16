package com.rentatools.iam.application.port.out;

import com.rentatools.iam.domain.model.HashedPassword;

/**
 * Puerto de salida para hashing y verificación de contraseñas.
 */
public interface PasswordHasher {

    HashedPassword hash(String rawPassword);

    boolean matches(String rawPassword, HashedPassword hashedPassword);
}
