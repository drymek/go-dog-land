#!/usr/bin/env bash
set -u

PROJECT_DIR="${1:-$(pwd)}"
REPORT_DIR="${PROJECT_DIR}/build"
REPORT_FILE="${REPORT_DIR}/godogland-diagnostics.txt"
GOLAND_SELECTOR="${GOLAND_SELECTOR:-GoLand2026.1}"
GOLAND_LOG_DIR="${HOME}/Library/Logs/JetBrains/${GOLAND_SELECTOR}"
GOLAND_CONFIG_DIR="${HOME}/Library/Application Support/JetBrains/${GOLAND_SELECTOR}"

mkdir -p "${REPORT_DIR}"
: > "${REPORT_FILE}"

section() {
  printf '\n===== %s =====\n' "$1" >> "${REPORT_FILE}"
}

run() {
  printf '\n$ %s\n' "$*" >> "${REPORT_FILE}"
  "$@" >> "${REPORT_FILE}" 2>&1 || true
}

append_file_matches() {
  local label="$1"
  local file="$2"
  local pattern="$3"

  section "${label}: ${file}"
  if [ ! -f "${file}" ]; then
    printf 'missing\n' >> "${REPORT_FILE}"
    return
  fi

  if command -v rg >/dev/null 2>&1; then
    rg -n -C 4 "${pattern}" "${file}" >> "${REPORT_FILE}" 2>&1 || true
  else
    grep -n -C 4 -E "${pattern}" "${file}" >> "${REPORT_FILE}" 2>&1 || true
  fi
}

section "diagnostic context"
printf 'project_dir=%s\n' "${PROJECT_DIR}" >> "${REPORT_FILE}"
printf 'goland_selector=%s\n' "${GOLAND_SELECTOR}" >> "${REPORT_FILE}"
printf 'report_file=%s\n' "${REPORT_FILE}" >> "${REPORT_FILE}"
date >> "${REPORT_FILE}" 2>&1 || true

section "system"
run uname -a
run sw_vers

section "go"
run which go
run go version
run go env GOROOT GOPATH GOMOD GOWORK GOFLAGS GO111MODULE

section "project"
run pwd
run ls -la "${PROJECT_DIR}"
run ls -la "${PROJECT_DIR}/features"
run find "${PROJECT_DIR}" -maxdepth 3 -name go.mod -o -name go.work -o -name "*_test.go" -o -name "*.feature"
run go -C "${PROJECT_DIR}" list -json .
run go -C "${PROJECT_DIR}" test -c -n .

section "installed goland apps"
run find /Applications -maxdepth 2 -name "GoLand*.app"
for plist in /Applications/GoLand*.app/Contents/Info.plist; do
  [ -f "${plist}" ] || continue
  append_file_matches "goland app info" "${plist}" "GoLand [0-9]|build GO-|CFBundleShortVersionString"
done

section "installed plugins"
run find "${GOLAND_CONFIG_DIR}/plugins" -maxdepth 4 -iname "*godog*" -o -iname "*gherkin*" -o -iname "plugin.xml"

append_file_matches "project workspace run configurations" "${PROJECT_DIR}/.idea/workspace.xml" "Godog|GoTestRunConfiguration|GoApplicationRunConfiguration|WORKING_DIRECTORY|ROOT_DIRECTORY|DIRECTORY|PACKAGE|kind|go test"

if [ -d "${PROJECT_DIR}/.idea/runConfigurations" ]; then
  for file in "${PROJECT_DIR}"/.idea/runConfigurations/*.xml; do
    [ -f "${file}" ] || continue
    append_file_matches "project shared run configuration" "${file}" "Godog|GoTestRunConfiguration|WORKING_DIRECTORY|ROOT_DIRECTORY|DIRECTORY|PACKAGE|kind|go test"
  done
fi

append_file_matches "goland options recent projects" "${GOLAND_CONFIG_DIR}/options/recentProjects.xml" "${PROJECT_DIR}|ledgercalc"
append_file_matches "goland idea log godog/go test" "${GOLAND_LOG_DIR}/idea.log" "Godog|GoDogLand|go test -c|GOROOT=|GOPATH=|no Go files|unknown flag -l"

section "done"
printf 'Attach this file when reporting the issue: %s\n' "${REPORT_FILE}" >> "${REPORT_FILE}"
printf '%s\n' "${REPORT_FILE}"
