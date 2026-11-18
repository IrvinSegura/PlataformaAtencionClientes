package com.irvinsegura.supportchat.actions.infrastructure.webhook;

import com.irvinsegura.supportchat.actions.domain.model.EndpointNotAllowedException;
import com.irvinsegura.supportchat.actions.domain.port.EndpointPolicy;
import com.irvinsegura.supportchat.shared.infrastructure.NetworkAddresses;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Webhooks must be public HTTPS services. Every address the host resolves to is checked, at save time and again
 * before each call, so a name that later resolves into a private network is caught too. A name that changes between
 * the check and the connection (DNS rebinding) is a remaining gap; a deployment that needs to close it routes webhook
 * traffic through an egress proxy that enforces the same rule.
 * <p>
 * Development and tests may allow plain HTTP and private addresses, to reach webhooks on the same machine.
 */
final class NetworkEndpointPolicy implements EndpointPolicy {

    private final boolean allowInsecureHttp;
    private final boolean allowPrivateNetwork;

    NetworkEndpointPolicy(boolean allowInsecureHttp, boolean allowPrivateNetwork) {
        this.allowInsecureHttp = allowInsecureHttp;
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    @Override
    public void check(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(allowInsecureHttp && scheme.equals("http"))) {
            throw new EndpointNotAllowedException("Webhooks must use https");
        }
        if (allowPrivateNetwork) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new EndpointNotAllowedException("The webhook's host name does not resolve");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new EndpointNotAllowedException("Webhooks cannot point to private, local or reserved "
                        + "network addresses");
            }
        }
    }

    static boolean isPublic(InetAddress address) {
        return NetworkAddresses.isPublic(address);
    }
}
