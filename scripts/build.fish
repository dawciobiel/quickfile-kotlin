#!/usr/bin/env fish

# QuickFile helper: run Gradle build-related tasks from the repository root.
#
# Usage:
#   ./scripts/build.fish
#   ./scripts/build.fish :app-ui:build
#   ./scripts/build.fish :app-ui:test
#   ./scripts/build.fish :plugin-api:build --warning-mode all

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
