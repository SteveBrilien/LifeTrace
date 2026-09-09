#!/usr/bin/env bash
set -Eeuo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workspace_root="$(cd "$project_root/.." && pwd)"
shared_gradle_home="$workspace_root/.codex-home/gradle-cache"
run_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
run_root="$workspace_root/.codex-home/lifetrace-gradle-runs/$run_stamp"
log_root="$project_root/build/robust-build-$run_stamp"
mkdir -p "$run_root" "$log_root"

for item in caches wrapper jdks; do
  if [[ -e "$shared_gradle_home/$item" && ! -e "$run_root/$item" ]]; then
    ln -s "$shared_gradle_home/$item" "$run_root/$item"
  fi
done

export GRADLE_USER_HOME="$run_root"
export CI=true
mode="${1:-all}"
if [[ "$mode" != "all" && "$mode" != "dex" ]]; then
  echo "[build] ERROR: usage: $0 [all|dex]"
  exit 64
fi

available_kib() {
  awk '/^MemAvailable:/ {print $2}' /proc/meminfo
}

echo "[build] run_stamp=$run_stamp"
echo "[build] project=$project_root"
echo "[build] gradle_home=$GRADLE_USER_HOME"
echo "[build] logs=$log_root"

minimum_kib=$((300 * 1024))
wait_deadline=$(( $(date +%s) + 1800 ))
while (( $(available_kib) < minimum_kib )); do
  now="$(date +%s)"
  if (( now >= wait_deadline )); then
    echo "[build] ERROR: MemAvailable remained below 300 MiB for 30 minutes."
    exit 75
  fi
  echo "[build] waiting_for_memory available_kib=$(available_kib) required_kib=$minimum_kib"
  sleep 30
done

echo "[build] memory_gate_passed available_kib=$(available_kib)"

jvm_args="-Xms64m -Xmx384m -XX:MaxMetaspaceSize=224m -XX:ReservedCodeCacheSize=24m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -Dfile.encoding=UTF-8"

capture_diagnostics() {
  local sid="$1"
  local label="$2"
  local diag="$log_root/diagnostics-$label.txt"
  {
    echo "=== DATE ==="
    date -u
    echo "=== MEMORY ==="
    cat /proc/meminfo
    echo "=== SESSION PROCESSES ==="
    ps -eo pid=,ppid=,sid=,state=,etimes=,%cpu=,%mem=,rss=,vsz=,wchan:24,args= |
      awk -v sid="$sid" '$3 == sid'
  } >"$diag" 2>&1 || true

  if command -v jcmd >/dev/null 2>&1; then
    while read -r java_pid; do
      [[ -n "$java_pid" ]] || continue
      {
        echo
        echo "=== JCMD THREAD.PRINT PID $java_pid ==="
        timeout 12s jcmd "$java_pid" Thread.print -l
      } >>"$diag" 2>&1 || true
    done < <(
      ps -eo pid=,sid=,comm= |
        awk -v sid="$sid" '$2 == sid && $3 == "java" {print $1}'
    )
  fi
}

stop_session() {
  local sid="$1"
  kill -TERM -- "-$sid" 2>/dev/null || true
  sleep 10
  kill -KILL -- "-$sid" 2>/dev/null || true
}

session_cpu_ticks() {
  local sid="$1"
  local total=0
  while read -r process_id; do
    [[ -r "/proc/$process_id/stat" ]] || continue
    read -r user_ticks system_ticks < <(
      awk '{print $14, $15}' "/proc/$process_id/stat" 2>/dev/null
    ) || true
    total=$(( total + ${user_ticks:-0} + ${system_ticks:-0} ))
  done < <(ps -eo pid=,sid= | awk -v sid="$sid" '$2 == sid {print $1}')
  printf '%s\n' "$total"
}

session_rss_kib() {
  local sid="$1"
  ps -eo sid=,rss= | awk -v sid="$sid" '$1 == sid {sum += $2} END {print sum + 0}'
}

run_stage() {
  local stage="$1"
  local task="$2"
  local max_seconds="$3"
  local idle_seconds="$4"
  local stage_log="$log_root/$stage.log"

  echo "[stage] name=$stage task=$task max_seconds=$max_seconds idle_seconds=$idle_seconds"
  (
    cd "$project_root"
    exec setsid ./gradlew "$task" \
      --offline \
      --no-daemon \
      --no-configuration-cache \
      --max-workers=1 \
      --console=plain \
      --stacktrace \
      --info \
      "-Dorg.gradle.jvmargs=$jvm_args" \
      -Dorg.gradle.workers.max=1 \
      -Dorg.gradle.parallel=false \
      -Dkotlin.compiler.execution.strategy=in-process
  ) > >(stdbuf -oL tee -a "$stage_log") 2>&1 &

  local leader=$!
  local sid=$leader
  local started
  started="$(date +%s)"
  local last_active=$started
  local previous_ticks=0
  local current_ticks=0
  local rc=0

  while kill -0 "$leader" 2>/dev/null; do
    sleep 30
    local now elapsed idle rss
    now="$(date +%s)"
    elapsed=$(( now - started ))
    current_ticks="$(session_cpu_ticks "$sid")"
    if (( current_ticks > previous_ticks )); then
      last_active=$now
    fi
    previous_ticks=$current_ticks
    idle=$(( now - last_active ))
    rss="$(session_rss_kib "$sid")"
    echo "[watch] stage=$stage elapsed_s=$elapsed cpu_idle_s=$idle cpu_ticks=$current_ticks rss_kib=$rss mem_available_kib=$(available_kib)"

    if (( elapsed >= max_seconds )); then
      echo "[watch] ERROR: stage $stage exceeded $max_seconds seconds."
      capture_diagnostics "$sid" "$stage-timeout"
      stop_session "$sid"
      wait "$leader" 2>/dev/null || true
      return 124
    fi

    if (( idle >= idle_seconds )); then
      echo "[watch] ERROR: stage $stage used no CPU for $idle_seconds seconds."
      capture_diagnostics "$sid" "$stage-stalled"
      stop_session "$sid"
      wait "$leader" 2>/dev/null || true
      return 125
    fi
  done

  set +e
  wait "$leader"
  rc=$?
  set -e
  echo "[stage] name=$stage returncode=$rc"
  return "$rc"
}

run_stage "dex" ":android:mergeExtDexDebug" 1500 600
if [[ "$mode" == "dex" ]]; then
  echo "[build] DEX_STAGE_SUCCESS"
  exit 0
fi
run_stage "assemble" ":android:assembleDebug" 1500 600

apk="$project_root/android/build/outputs/apk/debug/android-debug.apk"
if [[ ! -f "$apk" ]]; then
  echo "[build] ERROR: Gradle succeeded but APK was not found at $apk"
  exit 66
fi

size_bytes="$(stat -c '%s' "$apk")"
sha256="$(sha256sum "$apk" | awk '{print $1}')"
echo "[build] SUCCESS"
echo "[build] apk=$apk"
echo "[build] size_bytes=$size_bytes"
echo "[build] sha256=$sha256"
