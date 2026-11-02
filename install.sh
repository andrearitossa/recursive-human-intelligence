#!/bin/sh
# Installs RHI for the current user, with no Java needed. macOS and Linux.
set -eu
REPO="andrearitossa/recursive-human-intelligence"
case "$(uname -s)-$(uname -m)" in
  Darwin-arm64) ASSET=RHI-macos-arm64 ;;
  Darwin-x86_64) ASSET=RHI-macos-x64 ;;
  Linux-x86_64) ASSET=RHI-linux-x64 ;;
  *) echo "RHI has no build for $(uname -s) $(uname -m) yet." >&2; exit 1 ;;
esac
URL="${RHI_ARCHIVE:-https://github.com/$REPO/releases/latest/download/$ASSET.tar.gz}"
TEMP=$(mktemp -d); trap 'rm -rf "$TEMP"' EXIT
echo "Downloading RHI..."
case "$URL" in
  /*) cp "$URL" "$TEMP/rhi.tar.gz" ;;
  *) curl -fsSL "$URL" -o "$TEMP/rhi.tar.gz" ;;
esac
tar -xzf "$TEMP/rhi.tar.gz" -C "$TEMP"
mkdir -p "$HOME/.local/bin"
if [ "$(uname -s)" = Darwin ]; then
  mkdir -p "$HOME/Applications"; rm -rf "$HOME/Applications/RHI.app"; mv "$TEMP/RHI.app" "$HOME/Applications/RHI.app"
  printf '#!/bin/sh\nexec open "%s/Applications/RHI.app"\n' "$HOME" > "$HOME/.local/bin/rhi"
  chmod +x "$HOME/.local/bin/rhi"
else
  mkdir -p "$HOME/.local/share"; rm -rf "$HOME/.local/share/rhi"; mv "$TEMP/RHI" "$HOME/.local/share/rhi"
  ln -sf "$HOME/.local/share/rhi/bin/RHI" "$HOME/.local/bin/rhi"
fi
echo "RHI is installed. Start it with: $HOME/.local/bin/rhi"
