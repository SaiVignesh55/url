package com.url.shortener.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class IpAddressResolver {

    private final List<CidrBlock> trustedProxyCidrs;

    @Autowired
    public IpAddressResolver(@Value("${ip.resolver.trusted-proxies:127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128,fc00::/7,fe80::/10}") String trustedProxyCidrs) {
        this.trustedProxyCidrs = parseCidrs(trustedProxyCidrs);
    }


    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = normalizeSingleIp(request.getRemoteAddr());
        boolean trustForwardedHeaders = shouldTrustForwardedHeaders(remoteAddr);

        if (trustForwardedHeaders) {
            String xForwardedForIp = firstPublicIp(request.getHeader("X-Forwarded-For"));
            if (xForwardedForIp != null) {
                return xForwardedForIp;
            }

            String xRealIp = firstPublicIp(request.getHeader("X-Real-IP"));
            if (xRealIp != null) {
                return xRealIp;
            }

            String forwardedIp = firstPublicIp(request.getHeader("Forwarded"));
            if (forwardedIp != null) {
                return forwardedIp;
            }
        }

        return remoteAddr;
    }

    private String firstPublicIp(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }

        String[] parts = headerValue.split(",");
        for (String part : parts) {
            String candidate = normalizeSingleIp(part);
            if (candidate == null) {
                continue;
            }

            if (!isLoopbackIp(candidate) && !isPrivateIp(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean shouldTrustForwardedHeaders(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }

        return trustedProxyCidrs.stream().anyMatch(cidr -> cidr.contains(remoteAddr));
    }

    private static String normalizeSingleIp(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String ip = rawValue.trim();
        if (ip.startsWith("for=")) {
            ip = ip.substring(4).trim();
        }

        int semicolonIndex = ip.indexOf(';');
        if (semicolonIndex > 0) {
            ip = ip.substring(0, semicolonIndex).trim();
        }

        if (ip.startsWith("\"") && ip.endsWith("\"") && ip.length() > 1) {
            ip = ip.substring(1, ip.length() - 1);
        }

        if (ip.startsWith("[") && ip.contains("]")) {
            ip = ip.substring(1, ip.indexOf(']'));
        }

        if (ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }

        int colonIndex = ip.lastIndexOf(':');
        if (colonIndex > 0 && ip.contains(".") && ip.indexOf(':') == colonIndex) {
            ip = ip.substring(0, colonIndex);
        }

        if (!isValidIpLiteral(ip)) {
            return null;
        }

        return ip;
    }

    private static boolean isValidIpLiteral(String value) {
        if (value == null || value.isBlank() || !value.matches("^[0-9a-fA-F:.]+$")) {
            return false;
        }

        try {
            InetAddress parsed = InetAddress.getByName(value);
            return parsed != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isLoopbackIp(String ip) {
        return ip.startsWith("127.") || "0.0.0.0".equals(ip) || "::1".equals(ip);
    }

    private static boolean isPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        String normalized = ip.toLowerCase();
        if (normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("fe80:")) {
            return true;
        }

        if (!ip.contains(".")) {
            return false;
        }

        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("169.254.")) {
            return true;
        }

        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length > 1) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    return secondOctet >= 16 && secondOctet <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }

        return false;
    }

    private static List<CidrBlock> parseCidrs(String rawCidrs) {
        if (rawCidrs == null || rawCidrs.isBlank()) {
            return List.of();
        }

        List<CidrBlock> ranges = new ArrayList<>();
        Arrays.stream(rawCidrs.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(value -> {
                    try {
                        ranges.add(CidrBlock.parse(value));
                    } catch (IllegalArgumentException ignored) {
                        // Invalid CIDR entries are ignored so startup is resilient.
                    }
                });
        return List.copyOf(ranges);
    }

    static final class CidrBlock {
        private final byte[] networkBytes;
        private final BigInteger network;
        private final BigInteger mask;

        private CidrBlock(byte[] networkBytes, BigInteger network, BigInteger mask) {
            this.networkBytes = networkBytes;
            this.network = network;
            this.mask = mask;
        }

        static CidrBlock parse(String value) {
            String[] parts = value.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR");
            }

            try {
                InetAddress address = InetAddress.getByName(parts[0].trim());
                byte[] bytes = address.getAddress();
                int prefixLength = Integer.parseInt(parts[1].trim());
                int bitLength = bytes.length * 8;

                if (prefixLength < 0 || prefixLength > bitLength) {
                    throw new IllegalArgumentException("Invalid prefix");
                }

                BigInteger fullMask = BigInteger.ONE.shiftLeft(bitLength).subtract(BigInteger.ONE);
                BigInteger mask = prefixLength == 0
                        ? BigInteger.ZERO
                        : fullMask.shiftRight(bitLength - prefixLength).shiftLeft(bitLength - prefixLength);

                BigInteger network = new BigInteger(1, bytes).and(mask);
                return new CidrBlock(bytes, network, mask);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid CIDR", ex);
            }
        }

        boolean contains(String ip) {
            if (ip == null || ip.isBlank()) {
                return false;
            }

            try {
                InetAddress address = InetAddress.getByName(ip);
                byte[] candidate = address.getAddress();
                if (candidate.length != networkBytes.length) {
                    return false;
                }

                BigInteger ipValue = new BigInteger(1, candidate);
                return ipValue.and(mask).equals(network);
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
