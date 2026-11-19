#!/usr/bin/env bash
# Checks APK sizes against rules T1: fails above 100 MB, warns above the 25 MB target
# (1 MB = 1,048,576 bytes). Writes a table to the job summary when run in GitHub Actions.
# Usage: .github/scripts/apk-size.sh <apk>...   (APK_MAX_MB and APK_WARN_MB override the limits)
set -euo pipefail

max_mb=${APK_MAX_MB:-100}
warn_mb=${APK_WARN_MB:-25}
summary=${GITHUB_STEP_SUMMARY:-/dev/null}
status=0

if [ $# -eq 0 ]; then
    echo "usage: $0 <apk>..." >&2
    exit 2
fi

{
    echo "### APK size (rules T1: limit $max_mb MB, target $warn_mb MB)"
    echo
    echo "| APK | Size | Result |"
    echo "|---|---|---|"
} >> "$summary"

for apk in "$@"; do
    bytes=$(wc -c < "$apk")
    mb=$(awk "BEGIN { printf \"%.2f\", $bytes / 1048576 }")
    name=$(basename "$apk")
    if [ "$bytes" -gt $((max_mb * 1048576)) ]; then
        echo "::error file=$apk::$name is $mb MB, above the $max_mb MB limit (rules T1)"
        result="fails: over $max_mb MB"
        status=1
    elif [ "$bytes" -gt $((warn_mb * 1048576)) ]; then
        echo "::warning file=$apk::$name is $mb MB, above the $warn_mb MB target (rules T1)"
        result="warning: over $warn_mb MB"
    else
        echo "$name: $mb MB (target $warn_mb MB, limit $max_mb MB)"
        result="ok"
    fi
    echo "| \`$name\` | $mb MB | $result |" >> "$summary"
done

exit "$status"
