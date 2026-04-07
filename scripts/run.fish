#!/usr/bin/env fish

set -x TERM xterm-256color

clear

set_color --bold --background green black
echo "  RUNNING...  "
set_color normal
echo ""

cd (dirname (status -f))/..
./gradlew :app-ui:run
