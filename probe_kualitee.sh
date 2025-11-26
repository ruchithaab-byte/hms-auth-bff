#!/bin/bash

TOKEN="0d1e891dcfcc965a2822d9deb439ee7c"
BASE_URL="https://apiss.kualitee.com/api/v2"

echo "--- Probing Kualitee API ---"

# Function to test auth
test_auth() {
    local HEADER_NAME=$1
    local HEADER_VAL=$2
    local URL=$3
    local AUTH_TYPE=$4
    
    echo "Testing $AUTH_TYPE with $URL..."
    # Added User-Agent and Accept headers
    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$URL/users/detail" \
        -H "$HEADER_NAME: $HEADER_VAL" \
        -H "Content-Type: application/json" \
        -H "Accept: application/json" \
        -H "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    
    if [ "$RESPONSE" == "200" ]; then
        echo "✅ SUCCESS! ($AUTH_TYPE)"
        return 0
    else
        echo "❌ FAILED (HTTP $RESPONSE)"
        return 1
    fi
}

# 0. Test WAF Bypass (No Token)
echo "0. Testing WAF Bypass (No Token)..."
RESPONSE=$(curl -s -X GET "$BASE_URL/users/detail" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

echo "Response: $RESPONSE"
if [[ "$RESPONSE" == *"token missing"* ]]; then
    echo "✅ WAF Bypass Successful! API is reachable."
else
    echo "❌ WAF Blocked or Unexpected Response."
fi

# 1. Test Auth Methods
echo "1. Testing Authentication..."

# Try Query Param
if test_auth "Authorization" "None" "$BASE_URL?token=$TOKEN" "Query Param"; then
    AUTH_HEADER="QUERY_PARAM"
# Try Bearer
elif test_auth "Authorization" "Bearer $TOKEN" "$BASE_URL" "Bearer Token"; then
    AUTH_HEADER="Authorization: Bearer $TOKEN"
# Try Token (Standard)
elif test_auth "Token" "$TOKEN" "$BASE_URL" "Token Header"; then
    AUTH_HEADER="Token: $TOKEN"
# Try Authorization: Token (Django style)
elif test_auth "Authorization" "Token $TOKEN" "$BASE_URL" "Authorization: Token"; then
    AUTH_HEADER="Authorization: Token $TOKEN"
# Try Basic Auth (Token as username)
elif curl -s -o /dev/null -w "%{http_code}" -X GET "$BASE_URL/users/detail" -u "$TOKEN:" -H "Content-Type: application/json" -H "Accept: application/json" | grep -q "200"; then
    echo "✅ SUCCESS! (Basic Auth)"
    AUTH_HEADER="BASIC_AUTH" # Special flag
else
    echo "⚠️ All auth methods failed on apiss. Trying main domain..."
    BASE_URL="https://api.kualitee.com/api/v2"
    if test_auth "Authorization" "Bearer $TOKEN" "$BASE_URL" "Bearer Token (Main Domain)"; then
        AUTH_HEADER="Authorization: Bearer $TOKEN"
    else
        echo "⚠️ Main domain failed. Trying Tenant Domain (julleyonline)..."
        BASE_URL="https://julleyonline.kualitee.com/api/v2"
        if test_auth "Authorization" "Bearer $TOKEN" "$BASE_URL" "Bearer Token (Tenant Domain)"; then
            AUTH_HEADER="Authorization: Bearer $TOKEN"
        elif test_auth "Token" "$TOKEN" "$BASE_URL" "Token Header (Tenant Domain)"; then
            AUTH_HEADER="Token: $TOKEN"
        else
            echo "🔴 CRITICAL: Unable to authenticate with any method."
            exit 1
        fi
    fi
fi

echo "Using Base URL: $BASE_URL"
echo "Using Auth Header: $AUTH_HEADER"

# 2. Get Project ID
echo -e "\n2. Fetching Projects..."
curl -s -X POST "$BASE_URL/project/list" -H "$AUTH_HEADER" -H "Content-Type: application/json" -d '{}' > projects.json
cat projects.json | head -c 200
echo "..."

# 3. Get Status IDs
echo -e "\n3. Fetching Statuses..."
curl -s -X GET "$BASE_URL/test_case/status_list" -H "$AUTH_HEADER" -H "Content-Type: application/json" > statuses.json
cat statuses.json | head -c 200
echo "..."

# 4. Get Cycles (Need Project ID first, assuming we find one)
# We'll just try to list cycles generally if possible, or skip
echo -e "\n4. Fetching Cycles..."
curl -s -X POST "$BASE_URL/cycle/list" -H "$AUTH_HEADER" -H "Content-Type: application/json" -d '{}' > cycles.json
cat cycles.json | head -c 200
echo "..."
