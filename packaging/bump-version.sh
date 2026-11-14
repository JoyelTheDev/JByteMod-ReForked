#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

NEW_VERSION="${1:-}"
if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: $0 MAJOR.MINOR.PATCH" >&2
  exit 1
fi

CURRENT_VERSION="$(awk -F'[<>]' '/<version>/ {print $3; exit}' pom.xml)"
if [ "$CURRENT_VERSION" = "$NEW_VERSION" ]; then
  echo "Version is already $NEW_VERSION" >&2
  exit 1
fi

awk -v v="$NEW_VERSION" '
  !done && /<version>/ {
    sub(/<version>[^<]*<\/version>/, "<version>" v "</version>")
    done = 1
  }
  { print }
' pom.xml > pom.xml.tmp
mv pom.xml.tmp pom.xml

sed -e "s#badge/version-[0-9][0-9.]*-blue#badge/version-${NEW_VERSION}-blue#" README.md > README.md.tmp
mv README.md.tmp README.md

echo "Version bumped: $CURRENT_VERSION -> $NEW_VERSION"
echo
echo "Next steps:"
echo "  git add pom.xml README.md"
echo "  git commit -m \"Release v${NEW_VERSION}\""
echo "  git tag v${NEW_VERSION}"
echo "  git push origin HEAD v${NEW_VERSION}"
