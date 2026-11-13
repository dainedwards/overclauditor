#!/bin/bash
# Bump the -oc.N version, build, and publish to the local plugin repository Rider polls.
# Rider: Settings > Plugins > gear > Manage Plugin Repositories > add
#   file://$HOME/.overclauditor/repo/updatePlugins.xml
set -euo pipefail
cd "$(dirname "$0")/.."

# JDK 21: $JAVA_HOME if set, else the one macOS knows about.
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
fi
[ -n "${JAVA_HOME:-}" ] || { echo "Set JAVA_HOME to a JDK 21 (see docs/SETUP.md)" >&2; exit 1; }
export JAVA_HOME
REPO="$HOME/.overclauditor/repo"

cur=$(sed -n 's/^pluginVersion = //p' gradle.properties)
base=${cur%-oc.*}
n=${cur##*-oc.}
ver="$base-oc.$((n + 1))"
sed -i.bak "s/^pluginVersion = .*/pluginVersion = $ver/" gradle.properties && rm -f gradle.properties.bak

rm -rf build/distributions
./gradlew buildPlugin -q
zip="build/distributions/overclauditor-$ver.zip"
[ -f "$zip" ] || { echo "build produced no $zip" >&2; exit 1; }

mkdir -p "$REPO"
rm -f "$REPO"/overclauditor-*.zip
cp "$zip" "$REPO/"
since=$(sed -n 's/^pluginSinceBuild = //p' gradle.properties)
cat > "$REPO/updatePlugins.xml.tmp" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="com.overclauditor" url="file://$REPO/overclauditor-$ver.zip" version="$ver">
    <idea-version since-build="$since"/>
    <name>Overclauditor</name>
  </plugin>
</plugins>
EOF
mv "$REPO/updatePlugins.xml.tmp" "$REPO/updatePlugins.xml"

git add gradle.properties
git commit -q -m "Release $ver"
echo "published $ver -> $REPO"
