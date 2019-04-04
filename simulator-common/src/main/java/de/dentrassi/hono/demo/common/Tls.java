/*******************************************************************************
 * Copyright (c) 2018, 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.demo.common;

import java.security.SecureRandom;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.glutamate.lang.Environment;
import okhttp3.OkHttpClient.Builder;

public final class Tls {

    private Tls() {
    }

    public static boolean insecure() {
        return Environment.getAs("TLS_INSECURE", Boolean.FALSE, Boolean::parseBoolean);
    }

    public static boolean disabled() {
        return Environment.getAs("DISABLE_TLS", Boolean.FALSE, Boolean::parseBoolean);
    }

    public static void makeOkHttpInsecure(final Builder builder) {
        try {
            builder.hostnameVerifier(new HostnameVerifier() {

                @Override
                public boolean verify(final String hostname, final SSLSession session) {
                    return true;
                }
            });

            final X509TrustManager trustAllCerts = new X509TrustManager() {

                @Override
                public void checkClientTrusted(final java.security.cert.X509Certificate[] chain,
                        final String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(final java.security.cert.X509Certificate[] chain,
                        final String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[] {};
                }
            };

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustAllCerts }, new SecureRandom());

            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            builder.sslSocketFactory(sslSocketFactory, trustAllCerts);

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

}
