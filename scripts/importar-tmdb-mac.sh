#!/usr/bin/env bash
set -euo pipefail

# Copie o API Read Access Token do TMDB no Mac antes de executar.
# Uso: bash scripts/importar-tmdb-mac.sh IP_DA_TV
# Nenhuma chave é enviada ao GitHub ou impressa no Terminal.

if [ "$#" -ne 1 ]; then
  echo "Uso: bash scripts/importar-tmdb-mac.sh IP_DA_TV"
  exit 1
fi

IP="$1"
DEVICE="$IP:5555"
PACKAGE="io.github.up2gooseo.telaleve"

if ! command -v pbpaste >/dev/null 2>&1 || ! command -v adb >/dev/null 2>&1; then
  echo "Este utilitário requer macOS e Android Platform Tools (adb)."
  exit 1
fi

COUNT="$(pbpaste | wc -c | tr -d ' ')"
if [ "$COUNT" -lt 50 ]; then
  echo "Copie primeiro o API Read Access Token completo do TMDB."
  exit 1
fi

adb connect "$DEVICE"
adb -s "$DEVICE" shell "run-as $PACKAGE mkdir -p files"
pbpaste | adb -s "$DEVICE" shell "run-as $PACKAGE sh -c 'cat > files/tmdb_access_token'"
adb -s "$DEVICE" shell "run-as $PACKAGE test -s files/tmdb_access_token"
adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell am start -W -n "$PACKAGE/.HomeActivity"
echo "Token transferido por ADB. Confirme os resultados TMDB na TV."
