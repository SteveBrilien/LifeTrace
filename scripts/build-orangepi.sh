#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
java_home=${JAVA_HOME:-/home/orangepi/.local/opt/openjdk-17}
android_sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/home/orangepi/.local/share/android-sdk}}
gradle_home=${GRADLE_HOME:-/home/orangepi/.local/opt/gradle-8.11.1}
arm_tools=/home/orangepi/.local/opt/android-arm64-tools
arm_lib_android=$arm_tools/usr/lib/aarch64-linux-gnu/android
arm_lib_arch=$arm_tools/usr/lib/aarch64-linux-gnu
arm_lib_usr=$arm_tools/usr/lib
aapt2_override=${AAPT2_OVERRIDE:-/home/orangepi/.local/bin/aapt2}
gradle_bin=${GRADLE_BIN:-}
if [[ -z "$gradle_bin" ]]; then
  if [[ -x "$gradle_home/bin/gradle" ]]; then
    gradle_bin="$gradle_home/bin/gradle"
  else
    gradle_bin="$project_root/gradlew"
  fi
fi

export JAVA_HOME=$java_home
export ANDROID_HOME=$android_sdk
export ANDROID_SDK_ROOT=$android_sdk
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$project_root/.mcp/gradle-user-home}
export PATH=$java_home/bin:$gradle_home/bin:$PATH
export LD_LIBRARY_PATH=$arm_lib_android:$arm_lib_arch:$arm_lib_usr${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}

if [[ $# -eq 0 ]]; then
  set -- :android:assembleDebug
fi

# Keep local.properties valid in both the Orange Pi host and MCP's canonical sandbox.
printf 'sdk.dir=%s\n' "$android_sdk" > "$project_root/local.properties"

cd "$project_root"
exec "$gradle_bin" \
  --no-daemon \
  --max-workers=3 \
  "-Pandroid.aapt2FromMavenOverride=$aapt2_override" \
  "$@"
