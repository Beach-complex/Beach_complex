#!/usr/bin/env bash
set -Eeuo pipefail

readonly observability_mount_point="/opt/beach-observability"
readonly docker_unit="docker.service"
readonly docker_drop_in="/etc/systemd/system/docker.service.d/observability-mount.conf"
readonly installed_verifier="/usr/local/sbin/verify-observability-mount"
readonly verification_mode="${1:-}"

pass() {
  printf 'PASS %s\n' "$1"
}

fail() {
  printf 'FAIL %s\n' "$1" >&2
  exit 1
}

usage() {
  printf 'Usage: %s {pre-docker|healthy|mount-missing}\n' "$0" >&2
  exit 2
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is unavailable: $1"
}

verify_mount_identity() {
  local expected_source
  local expected_uuid
  local mounted_filesystem
  local mounted_source
  local mounted_uuid
  local root_source

  mountpoint -q "$observability_mount_point" \
    || fail "$observability_mount_point is not a mount point"
  pass "$observability_mount_point is mounted"

  if ! expected_source="$(
    findmnt \
      --fstab \
      --first-only \
      --raw \
      --noheadings \
      --target "$observability_mount_point" \
      --output SOURCE
  )"; then
    fail "fstab entry is missing for $observability_mount_point"
  fi
  [[ "$expected_source" == UUID=?* ]] \
    || fail "fstab source is not a UUID entry for $observability_mount_point"
  expected_uuid="${expected_source#UUID=}"

  mounted_uuid="$(findmnt -rn --mountpoint "$observability_mount_point" -o UUID)"
  [[ -n "$mounted_uuid" ]] || fail "mounted filesystem UUID is unavailable"
  [[ "${mounted_uuid,,}" == "${expected_uuid,,}" ]] \
    || fail "mounted UUID does not match the fstab UUID"
  pass "mounted UUID matches the fstab UUID"

  mounted_filesystem="$(findmnt -rn --mountpoint "$observability_mount_point" -o FSTYPE)"
  [[ "$mounted_filesystem" == "ext4" ]] || fail "mounted filesystem is not ext4"
  pass "mounted filesystem is ext4"

  mounted_source="$(findmnt -rn --mountpoint "$observability_mount_point" -o SOURCE)"
  root_source="$(findmnt -rn --target / -o SOURCE)"
  [[ "$mounted_source" != "$root_source" ]] \
    || fail "observability data path resolves to the root filesystem"
  pass "observability data path is separate from the root filesystem"
}

verify_docker_drop_in() {
  local loaded_drop_ins

  [[ -f "$docker_drop_in" ]] || fail "Docker mount dependency drop-in is missing"
  grep -Fxq "RequiresMountsFor=$observability_mount_point" "$docker_drop_in" \
    || fail "Docker drop-in does not require the observability mount"
  grep -Fxq "ExecStartPre=$installed_verifier pre-docker" "$docker_drop_in" \
    || fail "Docker drop-in does not run the mount verifier"

  loaded_drop_ins="$(systemctl show "$docker_unit" -p DropInPaths --value)"
  [[ "$loaded_drop_ins" == *"$docker_drop_in"* ]] \
    || fail "systemd has not loaded the Docker mount dependency drop-in"
  pass "Docker mount dependency drop-in is loaded"
}

verify_mount_precedes_docker() {
  local docker_active_at
  local mount_active_at
  local mount_unit

  mount_unit="$(systemd-escape --path --suffix=mount "$observability_mount_point")"
  mount_active_at="$(systemctl show "$mount_unit" -p ActiveEnterTimestampMonotonic --value)"
  docker_active_at="$(systemctl show "$docker_unit" -p ActiveEnterTimestampMonotonic --value)"

  [[ "$mount_active_at" =~ ^[0-9]+$ && "$mount_active_at" -gt 0 ]] \
    || fail "mount activation timestamp is unavailable"
  [[ "$docker_active_at" =~ ^[0-9]+$ && "$docker_active_at" -gt 0 ]] \
    || fail "Docker activation timestamp is unavailable"
  ((mount_active_at < docker_active_at)) \
    || fail "Docker became active before the observability mount"
  pass "observability mount became active before Docker"
}

verify_root_backed_directory_is_empty() {
  local first_entry

  if [[ ! -e "$observability_mount_point" ]]; then
    pass "unmounted observability path does not exist on the root filesystem"
    return
  fi

  [[ -d "$observability_mount_point" ]] \
    || fail "unmounted observability path is not a directory"
  first_entry="$(find "$observability_mount_point" -mindepth 1 -maxdepth 1 -print -quit)"
  [[ -z "$first_entry" ]] \
    || fail "unmounted observability path contains root-backed data: $first_entry"
  pass "unmounted observability path contains no root-backed data"
}

verify_pre_docker() {
  verify_mount_identity
}

verify_healthy() {
  verify_docker_drop_in
  verify_mount_identity
  systemctl is-active --quiet "$docker_unit" || fail "Docker is not active"
  pass "Docker is active"
  verify_mount_precedes_docker
}

verify_mount_missing() {
  local docker_state

  verify_docker_drop_in
  mountpoint -q "$observability_mount_point" \
    && fail "$observability_mount_point is unexpectedly mounted"
  pass "$observability_mount_point is not mounted"

  docker_state="$(systemctl is-active "$docker_unit" 2>/dev/null || true)"
  case "$docker_state" in
    inactive | failed)
      pass "Docker is not active while the observability mount is missing"
      ;;
    *)
      fail "unexpected Docker state while the observability mount is missing: $docker_state"
      ;;
  esac

  verify_root_backed_directory_is_empty
}

require_command find
require_command findmnt
require_command grep
require_command mountpoint
require_command systemctl
require_command systemd-escape

case "$verification_mode" in
  pre-docker)
    verify_pre_docker
    ;;
  healthy)
    verify_healthy
    ;;
  mount-missing)
    verify_mount_missing
    ;;
  *)
    usage
    ;;
esac
