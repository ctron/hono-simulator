/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
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

import com.fasterxml.jackson.annotation.JsonProperty;

public class AddCredentials {

    public static class Secret {

        private static final SecureRandom r = new SecureRandom();

        @JsonProperty("pwd-hash")
        private String passwordHash;

        private String salt;

        @JsonProperty("hash-function")
        private String hashFunction;

        public void setHashFunction(final String hashFunction) {
            this.hashFunction = hashFunction;
        }

        public void setPasswordHash(final String passwordHash) {
            this.passwordHash = passwordHash;
        }

        public void setSalt(final String salt) {
            this.salt = salt;
        }

        public String getHashFunction() {
            return this.hashFunction;
        }

        public String getPasswordHash() {
            return this.passwordHash;
        }

        public String getSalt() {
            return this.salt;
        }

        public static Secret sha512(final String password) {
            try {
                final MessageDigest md = MessageDigest.getInstance("SHA-512");

                final byte[] salt = new byte[4];
                r.nextBytes(salt);

                final Secret result = new Secret();
                result.setSalt(Base64.getEncoder().encodeToString(salt));
                result.setHashFunction("sha-512");

                md.update(salt);
                final byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));

                result.setPasswordHash(Base64.getEncoder().encodeToString(hash));

                return result;

            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String type;

    @JsonProperty("device-id")
    private String deviceId;

    @JsonProperty("auth-id")
    private String authId;

    private List<Secret> secrets = new ArrayList<>();

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

    public void setSecrets(final List<Secret> secrets) {
        this.secrets = secrets;
    }

    public List<Secret> getSecrets() {
        return this.secrets;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }

}
