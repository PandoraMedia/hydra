package com.pandora.hydra.client;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ProxyConfigTest {
    private static void setProxySystemProperties(String host, String port) {
        System.setProperty("http.proxyPort", port);
        System.setProperty("http.proxyHost", host);
    }

    @Test
    public void test1() {
        setProxySystemProperties("", "");
        assertNull(HydraClient.createProxyIfSpecified());

        setProxySystemProperties("", "5005");
        assertNull(HydraClient.createProxyIfSpecified());

        setProxySystemProperties("foobar.com", "");
        assertNull(HydraClient.createProxyIfSpecified());

        setProxySystemProperties("foobar.com", "5005");
        assertNotNull(HydraClient.createProxyIfSpecified());
    }
}
