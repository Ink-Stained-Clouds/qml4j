#!/usr/bin/env bash
# Run a QML project, quickshell-style:  `./run.sh <projectDir> <entry.qml>`
#   e.g.  ./run.sh shared-qml showcases/FisProxyShowcase.qml
# `./run.sh app [light]` runs the bundled upstream MD3 app from $MCQ_DIR (default ../mcq;
# clone once: git clone https://github.com/sudoevolve/material-components-qml ../mcq).
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
# Dark by default; `./run.sh app light` or QML4J_DARK=false picks the light scheme.
DARK="${QML4J_DARK:-true}"
[ "${2:-}" = "light" ] && DARK=false
# `QML4J_FPS=true ./run.sh app` shows a top-right FPS overlay; QML4J_VSYNC=false uncaps
# the frame loop (otherwise vsync pins it to the monitor refresh, ~60fps);
# QML4J_CANVAS_CACHE=false falls back to per-frame direct canvas draw (cache on by default).
exec java -cp "$CP" -Dqml4j.mcq="${MCQ_DIR:-$PWD/../mcq}" -Dqml4j.dark="$DARK" \
    -Dqml4j.fps="${QML4J_FPS:-false}" -Dqml4j.vsync="${QML4J_VSYNC:-true}" \
    -Dqml4j.canvasCache="${QML4J_CANVAS_CACHE:-true}" \
    io.github.timer_err.qml4j.demo.DesktopMain "$@"
