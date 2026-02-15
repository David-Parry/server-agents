package com.davidparry.agent.security;

import com.davidparry.agent.config.TokenHashingProperties;

import java.util.Map;
import java.util.UUID;

/**
 * Standalone utility to generate token hashes for HTTP request headers.
 * Uses TokenHashingService directly.
 *
 * Usage:
 *   java TokenHashGenerator <token> <customerId> <secret> [secretVersion]
 */
public final class TokenHashGenerator {

    private TokenHashGenerator() {
        // Utility class - prevent instantiation
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        String token = args[0];
        String customerIdStr = args[1];
        String secret = args[2];
        String secretVersion = args.length > 3 ? args[3] : "V1";

        UUID customerId;
        try {
            customerId = UUID.fromString(customerIdStr);
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: Invalid UUID format for customerId: " + customerIdStr);
            System.exit(1);
            return;
        }

        // Create TokenHashingProperties with the provided secret
        TokenHashingProperties properties = new TokenHashingProperties(
            Map.of(secretVersion, secret),
            secretVersion
        );

        // Create TokenHashingService instance
        TokenHashingService hashingService = new TokenHashingService(properties);

        // Generate hash using the service
        TokenHashingService.HashResult result = hashingService.hashToken(token, customerId);

        System.out.println("=".repeat(60));
        System.out.println("Token Hash Generator");
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("Input:");
        System.out.println("  Customer ID:    " + customerId);
        System.out.println("  Secret Version: " + result.secretVersion());
        System.out.println("  Token (first 50 chars): " + truncate(token, 50));
        System.out.println();
        System.out.println("Output:");
        System.out.println("  Hash: " + result.hash());
        System.out.println();
        System.out.println("HTTP Header Usage:");
        System.out.println("  Authorization: Bearer " + token);
        System.out.println();
        System.out.println("Database Storage:");
        System.out.println("  token_hash:     " + result.hash());
        System.out.println("  secret_version: " + result.secretVersion());
        System.out.println("=".repeat(60));
    }

    private static String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength) + "...";
    }

    private static void printUsage() {
        System.out.println("Token Hash Generator for Spring Agents");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java TokenHashGenerator <token> <customerId> <secret> [secretVersion]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  token          - The JWT token to hash");
        System.out.println("  customerId     - The customer's UUID (used as salt)");
        System.out.println("  secret         - The hashing secret (min 32 characters)");
        System.out.println("  secretVersion  - Optional version tag (default: V1)");
    }
}
