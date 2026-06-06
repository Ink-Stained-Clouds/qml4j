#!/usr/bin/env bash
# Run the desktop launcher, or a single showcase: `./run.sh GridFlowShowcase`.
#
# Builds qml4j-core + the desktop module from source (reactor, `-am`) and launches
# java directly with the freshly-compiled classes placed AHEAD of any ~/.m2
# artifact on the classpath. So edits to the engine take effect on the next run
# with no `mvn install` -- and a stale ~/.m2/qml4j-core jar can never shadow them.
set -euo pipefail
cd "$(dirname "$0")"

mvn -q -pl qml4j-demo-desktop -am compile

CP_FILE="$PWD/qml4j-demo-desktop/target/run-cp.txt"
mvn -q -pl qml4j-demo-desktop dependency:build-classpath \
    -Dmdep.includeScope=runtime -Dmdep.outputFile="$CP_FILE" >/dev/null

CP="$PWD/qml4j-demo-desktop/target/classes:$PWD/qml4j-core/target/classes:$(cat "$CP_FILE")"
exec java -cp "$CP" io.qml4j.demo.DesktopMain ${1:+"$1"}
