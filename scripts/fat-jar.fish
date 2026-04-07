#!/usr/bin/env fish

set -x TERM xterm-256color

clear

set_color --bold --background cyan black
echo "  PACKAGING UBER JAR...  "
set_color normal
echo ""

cd (dirname (status -f))/..
./gradlew :app-ui:packageUberJarForCurrentOS
