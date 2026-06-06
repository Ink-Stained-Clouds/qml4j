#!/usr/bin/env bash
# Run the desktop launcher, or a single showcase: `./run.sh GridFlowShowcase`.
#
# Builds + installs qml4j-core (reactor, `-am`) so dependency:build-classpath can
# resolve it, then launches java with the freshly-compiled target/classes placed
# AHEAD of the ~/.m2 jar on the classpath -- edits to the engine take effect
# immediately and a stale ~/.m2/qml4j-core jar can never shadow them.
set -euo pipefail
cd "$(dirname "$0")"

mvn -q -pl qml4j-demo-desktop -am install -DskipTests

CP_FILE="$PWD/qml4j-demo-desktop/target/run-cp.txt"
mvn -q -pl qml4j-demo-desktop dependency:build-classpath \
    -Dmdep.includeScope=runtime -Dmdep.outputFile="$CP_FILE" >/dev/null

CP="$PWD/qml4j-demo-desktop/target/classes:$PWD/qml4j-core/target/classes:$(cat "$CP_FILE")"
exec java -cp "$CP" io.qml4j.demo.DesktopMain ${1:+"$1"}
