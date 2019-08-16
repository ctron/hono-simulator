/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.demo.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.eclipse.hono.service.management.credentials.PasswordSecret;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AddCredentials {

    private static final SecureRandom r = new SecureRandom();

    public static PasswordSecret digestPassword(final String password,
            final String javaAlgorithm, final String honoAlgorithm) {

        try {
            final MessageDigest md = MessageDigest.getInstance(javaAlgorithm);

            final byte[] salt = new byte[4];
            r.nextBytes(salt);

            final PasswordSecret result = new PasswordSecret();
            result.setSalt(Base64.getEncoder().encodeToString(salt));
            result.setHashFunction(honoAlgorithm);

            md.update(salt);
            final byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));

            result.setPasswordHash(Base64.getEncoder().encodeToString(hash));

            return result;

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static PasswordSecret sha512(final String password) {
        return digestPassword(password, "SHA-512", "sha-512");
    }

    public static PasswordSecret sha256(final String password) {
        return digestPassword(password, "SHA-256", "sha-256");
    }

    private String type;

    @JsonProperty("device-id")
    private String deviceId;

    @JsonProperty("auth-id")
    private String authId;

    private List<PasswordSecret> secrets = new ArrayList<>();

    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public void setAuthId(final String authId) {
        this.authId = authId;
    }

    public String getAuthId() {
        return this.authId;
    }

    public void setSecrets(final List<PasswordSecret> secrets) {
        this.secrets = secrets;
    }

    public List<PasswordSecret> getSecrets() {
        return this.secrets;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }

}
