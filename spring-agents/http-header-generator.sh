#!/bin/bash
# =============================================================================
# Token Hash Generator Script
# =============================================================================
# Reads hashing secrets from application-local.yml and generates token hashes
# for use in HTTP request headers.
#
# Usage:
#   ./http-header-generator.sh <jwt-token> <customer-uuid> [secret-version]
#
# Example:
#   ./http-header-generator.sh "eyJhbG..." "75d75941-1d5b-4db5-a7c5-b8561c5402e0" "V1"
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/src/main/resources/application-local.yml"

# Default values
DEFAULT_CUSTOMER_UUID="75d75941-1d5b-4db5-a7c5-b8561c5402e0"
DEFAULT_SECRET_VERSION="V1"

# Check arguments
if [ $# -lt 1 ]; then
    echo "Usage: $0 <jwt-token> [customer-uuid] [secret-version]"
    echo ""
    echo "Arguments:"
    echo "  jwt-token       - The JWT token to hash"
    echo "  customer-uuid   - Optional: The customer's UUID (default: $DEFAULT_CUSTOMER_UUID)"
    echo "  secret-version  - Optional: V1, V2, etc. (default: $DEFAULT_SECRET_VERSION)"
    exit 1
fi

JWT_TOKEN="$1"
CUSTOMER_UUID="${2:-$DEFAULT_CUSTOMER_UUID}"
SECRET_VERSION="${3:-$DEFAULT_SECRET_VERSION}"

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo "ERROR: Configuration file not found: $CONFIG_FILE"
    exit 1
fi

# Extract the hashing secret for the specified version from application-local.yml
# Uses grep and sed to parse the YAML (avoids requiring yq dependency)
extract_secret() {
    local version="$1"
    local in_hashing_secrets=false
    local secret=""

    while IFS= read -r line; do
        # Check if we're entering the hashing-secrets block
        if [[ "$line" =~ ^[[:space:]]*hashing-secrets: ]]; then
            in_hashing_secrets=true
            continue
        fi

        # If we're in hashing-secrets, look for our version
        if $in_hashing_secrets; then
            # Check if we've exited the hashing-secrets block (non-indented line or new key at same level)
            if [[ "$line" =~ ^[[:space:]]{4}[a-z] && ! "$line" =~ ^[[:space:]]*V[0-9] ]]; then
                in_hashing_secrets=false
                continue
            fi

            # Look for the version key (e.g., "V1: secret-value")
            if [[ "$line" =~ ^[[:space:]]*${version}:[[:space:]]*(.*) ]]; then
                secret="${BASH_REMATCH[1]}"
                # Remove any trailing comments and whitespace
                secret="${secret%%#*}"
                secret="${secret%"${secret##*[![:space:]]}"}"
                echo "$secret"
                return 0
            fi
        fi
    done < "$CONFIG_FILE"

    return 1
}

HASHING_SECRET=$(extract_secret "$SECRET_VERSION")

if [ -z "$HASHING_SECRET" ]; then
    echo "ERROR: Could not find hashing secret for version '$SECRET_VERSION' in $CONFIG_FILE"
    echo ""
    echo "Expected format in application-local.yml:"
    echo "  agent:"
    echo "    security:"
    echo "      hashing-secrets:"
    echo "        $SECRET_VERSION: your-secret-value"
    exit 1
fi

echo "Using secret version: $SECRET_VERSION"
echo "Config file: $CONFIG_FILE"
echo ""

# Run the TokenHashGenerator using Gradle (includes all dependencies)
# Use environment variables instead of -P flags to avoid exposing secrets in process listings
cd "$SCRIPT_DIR" && \
    TOKEN_HASH_TOKEN="$JWT_TOKEN" \
    TOKEN_HASH_CUSTOMER_ID="$CUSTOMER_UUID" \
    TOKEN_HASH_SECRET="$HASHING_SECRET" \
    TOKEN_HASH_SECRET_VERSION="$SECRET_VERSION" \
    ./gradlew -q runTokenHashGenerator
