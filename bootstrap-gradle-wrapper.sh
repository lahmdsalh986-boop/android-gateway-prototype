#!/usr/bin/env bash
set -euo pipefail
# Run once on a development machine with Gradle 8.10.2 to generate the standard wrapper files.
gradle wrapper --gradle-version 8.10.2 --distribution-type bin
