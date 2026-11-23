#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec java -cp 'classes:lib/*' bayt.Main
