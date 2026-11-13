package com.irvinsegura.supportchat.shared.infrastructure;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Tells public internet addresses from private, local and reserved ones, so that hosts a workspace enters (its action
 * webhooks, its mail servers) cannot point the platform at its own network.
 */
public final class NetworkAddresses {

    private NetworkAddresses() {
    }

    /**
     * Whether every address the host resolves to is public.
     *
     * @throws UnknownHostException when the name does not resolve
     */
    public static boolean allPublic(String host) throws UnknownHostException {
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (!isPublic(address)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 0.0.0.0/8 "this network", 100.64.0.0/10 carrier-grade NAT, 192.0.0.0/24 protocol assignments,
            // 198.18.0.0/15 benchmarking, 240.0.0.0/4 reserved
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (bytes[2] & 0xFF) == 0)
                    && !(first == 198 && (second == 18 || second == 19))
                    && first < 240;
        }
        if (address instanceof Inet6Address) {
            // fc00::/7 unique local addresses, which isSiteLocalAddress does not cover
            if ((bytes[0] & 0xFE) == 0xFC) {
                return false;
            }
            // IPv4-mapped (::ffff:a.b.c.d): judge the IPv4 address it carries
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= bytes[i] == 0;
            }
            if (mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }
}
