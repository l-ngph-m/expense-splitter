#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/bin/java" -m expense.splitter/org.Main "$@"
