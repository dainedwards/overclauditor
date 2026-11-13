#!/bin/bash
# Bump the -oc.N version, build, and publish to the local plugin repository Rider polls.
# Rider: Settings > Plugins > gear > Manage Plugin Repositories > add
#   file://$HOME/.overclauditor/repo/updatePlugins.xml
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home}"
REPO="$HOME/.overclauditor/repo"

cur=$(sed -n 's/^pluginVersion = //p' gradle.properties)
base=${cur%-oc.*}
n=${cur##*-oc.}
ver="$base-oc.$((n + 1))"
sed -i '' "s/^pluginVersion = .*/pluginVersion = $ver/" gradle.properties

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
