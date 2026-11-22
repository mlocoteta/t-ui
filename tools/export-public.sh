#!/usr/bin/env bash
# Build the would-be public tree: the project minus every path in .export-exclude (owner-only add-ons), minus build
# output. Usage: tools/export-public.sh DEST   (DEST must not exist or be empty). Publishes nothing.
# The public repo must start from this tree as ONE fresh initial commit (no history from any private tree).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; dst="$1"
[ -e "$dst" ] && [ -n "$(ls -A "$dst" 2>/dev/null)" ] && { echo "$dst is not empty" >&2; exit 1; }
mkdir -p "$dst"
ex=$(grep -v '^#' "$here/.export-exclude" 2>/dev/null | sed '/^$/d' | sed 's|/$||')
args=(--exclude=.git --exclude=.gradle --exclude=build --exclude=.export-exclude)
for e in $ex; do args+=(--exclude="/$e"); done
rsync -a "${args[@]}" "$here/" "$dst/"
printf '# Paths (prefixes) never part of a public export (owner-only add-ons live outside the public repo).\n' > "$dst/.export-exclude"
echo "exported $(find "$dst" -type f | wc -l) files -> $dst"
