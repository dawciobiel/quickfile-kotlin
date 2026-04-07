#!/usr/bin/env fish

# QuickFile helper: build an OS-specific Compose Desktop uber jar.
#
# Usage:
#   ./scripts/fat-jar.fish

set -x TERM xterm-256color

clear

set_color --bold --background cyan black
echo "  PACKAGING UBER JAR...  "
set_color normal
echo ""

cd (dirname (status -f))/..
./gradlew :app-ui:packageUberJarForCurrentOS
