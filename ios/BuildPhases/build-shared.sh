#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

CONFIGURATION_NAME="${CONFIGURATION:-}"
FRAMEWORK_TASK_SUFFIX="Debug"
if [[ -n "$CONFIGURATION_NAME" ]]; then
  CONFIGURATION_LOWER="$(printf '%s' "$CONFIGURATION_NAME" | tr '[:upper:]' '[:lower:]')"
  if [[ "$CONFIGURATION_LOWER" == "release" ]]; then
    FRAMEWORK_TASK_SUFFIX="Release"
  fi
fi

TARGET_TASKS=()

if [[ -z "${SDK_NAME:-}" ]]; then
  if [[ -n "$CONFIGURATION_NAME" ]]; then
    TARGET_TASKS=(
      ":shared:link${FRAMEWORK_TASK_SUFFIX}FrameworkIosArm64"
      ":shared:link${FRAMEWORK_TASK_SUFFIX}FrameworkIosSimulatorArm64"
    )
  else
    TARGET_TASKS=(
      ":shared:linkDebugFrameworkIosArm64"
      ":shared:linkDebugFrameworkIosSimulatorArm64"
      ":shared:linkReleaseFrameworkIosArm64"
      ":shared:linkReleaseFrameworkIosSimulatorArm64"
    )
  fi
elif [[ "${SDK_NAME}" == *simulator* ]]; then
  TARGET_TASKS=(":shared:link${FRAMEWORK_TASK_SUFFIX}FrameworkIosSimulatorArm64")
else
  TARGET_TASKS=(":shared:link${FRAMEWORK_TASK_SUFFIX}FrameworkIosArm64")
fi

echo "Building shared framework with tasks: ${TARGET_TASKS[*]}"
./gradlew "${TARGET_TASKS[@]}" --no-daemon
