#!/usr/bin/env bash
# Builds the Windows and macOS packages of Kai from one input folder (run on a Mac):
#   <in>/*.jar           the fat jar (./mvnw package)
#   <in>/winjre/         jlinked Java runtime for Windows x64
#   <in>/macjre/         jlinked Java runtime for macOS
#   <in>/kai.properties  settings shipped to users
#   <in>/README*         user readme
# Output: packaging/dist/{windows,macos}/ with kai(.exe), kai.properties, README,
#         plus kai-windows.zip and kai-macos.zip (the zip keeps the Mac exec bit).
# Usage:  packaging/build.sh <in>      MAC_ARCH=amd64 for an Intel macjre (default arm64)
set -euo pipefail

in=$(cd "${1:?usage: build.sh <input folder>}" && pwd)
here=$(cd "$(dirname "$0")" && pwd)
out="$here/dist"
mac_arch=${MAC_ARCH:-arm64}

fail() { echo "$*" >&2; exit 1; }
one() { # the single file matching a pattern in <in>
  local m=("$in"/$1)
  [[ ${#m[@]} -eq 1 && -e ${m[0]} ]] || fail "Need exactly one $1 in $in"
  echo "${m[0]}"
}
jar=$(one '*.jar')
readme=$(one 'README*')
for f in winjre/bin/java.exe macjre/bin/java kai.properties; do
  [[ -e $in/$f ]] || fail "Missing $in/$f"
done
command -v go >/dev/null || fail "Go is not installed (brew install go)"

# build <goos> <goarch> <jre folder> <program name> <package name>
build() {
  local stage dir="$out/$5"
  stage=$(mktemp -d)
  cp -R "$in/$3" "$stage/jre"
  cp "$jar" "$stage/kai.jar"
  # -h: store symlink targets as plain files; COPYFILE_DISABLE: no macOS ._ files
  COPYFILE_DISABLE=1 tar -czhf "$here/launcher/payload.tar.gz" -C "$stage" jre kai.jar
  rm -rf "$stage"
  mkdir -p "$dir"
  (cd "$here/launcher" && GOOS=$1 GOARCH=$2 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o "$dir/$4" .)
  cp "$in/kai.properties" "$readme" "$dir/"
  (cd "$out" && zip -qr "kai-$5.zip" "$5")
  echo "Built $dir"
}

rm -rf "$out"
build windows amd64 winjre kai.exe windows
build darwin "$mac_arch" macjre kai macos
rm "$here/launcher/payload.tar.gz"
