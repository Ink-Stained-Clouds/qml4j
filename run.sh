#!/usr/bin/env bash
# Run the desktop launcher, or a single showcase: `./run.sh GridFlowShowcase`.
# `./run.sh app` runs the upstream MD3 app from $MCQ_DIR (default ../mcq alongside
# this repo); clone it once: git clone https://github.com/sudoevolve/material-components-qml ../mcq
# `./run.sh app light` starts the app in the light scheme (default is dark).
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
# `./run.sh app light` -> light scheme; anything else keeps the default dark.
DARK=true
[ "${2:-}" = "light" ] && DARK=false
# `QML4J_FPS=true ./run.sh app` shows a top-right FPS overlay; QML4J_VSYNC=false uncaps
# the frame loop (otherwise vsync pins it to the monitor refresh, ~60fps);
# QML4J_CANVAS_CACHE=true opts into the offscreen canvas cache (off by default; its GPU
# blit is unreliable on some GL drivers).
exec java -cp "$CP" -Dqml4j.mcq="${MCQ_DIR:-$PWD/../mcq}" -Dqml4j.dark="$DARK" \
    -Dqml4j.fps="${QML4J_FPS:-false}" -Dqml4j.vsync="${QML4J_VSYNC:-true}" \
    -Dqml4j.canvasCache="${QML4J_CANVAS_CACHE:-false}" \
    io.qml4j.demo.DesktopMain ${1:+"$1"}
