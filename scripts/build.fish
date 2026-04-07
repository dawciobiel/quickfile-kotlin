#!/usr/bin/env fish

set -x TERM xterm-256color

clear

set_color --bold --background yellow black
echo "  BUILDING...  "
set_color normal
echo ""

cd (dirname (status -f))/..

if test (count $argv) -gt 0
    ./gradlew $argv
else
    ./gradlew :app-ui:build
end
