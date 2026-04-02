package com.url.shortener.service;

import com.url.shortener.exception.InvalidUrlException;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

@Service
public class SsrfProtectionService {

    public void assertPublicDestination(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL host is required");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException ex) {
            throw new InvalidUrlException("URL host cannot be resolved");
        }

        if (addresses.length == 0) {
            throw new InvalidUrlException("URL host cannot be resolved");
        }

        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new InvalidUrlException("Destination is not publicly routable");
            }
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);

            if (first == 0 || first == 10 || first == 127) {
                return false;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            if (first == 169 && second == 254) {
                return false;
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return false;
            }
            if (first == 192 && second == 168) {
                return false;
            }
            if (first >= 224) {
                return false;
            }
            return true;
        }

        if (address instanceof Inet6Address) {
            // block unique local fc00::/7
            if ((bytes[0] & (byte) 0xFE) == (byte) 0xFC) {
                return false;
            }
            // block link local fe80::/10
            if ((bytes[0] & (byte) 0xFF) == (byte) 0xFE && (bytes[1] & (byte) 0xC0) == (byte) 0x80) {
                return false;
            }
            // block documentation addresses 2001:db8::/32
            if (bytes.length >= 4
                    && bytes[0] == 0x20
                    && bytes[1] == 0x01
                    && bytes[2] == 0x0d
                    && bytes[3] == (byte) 0xb8) {
                return false;
            }
            return !Arrays.equals(bytes, InetAddress.getLoopbackAddress().getAddress());
        }

        return false;
    }
}

