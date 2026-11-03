#!/usr/bin/env bash
# Télécharge le moteur sherpa-onnx (bibliothèques Android) et le modèle de voix Supertonic 3.
# Versions figées : la liaison Java (com.k2fsa.sherpa.onnx) correspond à cette version de la bibliothèque.
set -euo pipefail
SHERPA_VERSION="2.0.1"
MODEL="sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download"

echo "→ sherpa-onnx v$SHERPA_VERSION (Android)"
curl -fL --retry 4 -o "$WORK/libs.tar.bz2" "$BASE/v$SHERPA_VERSION/sherpa-onnx-v$SHERPA_VERSION-android.tar.bz2"
mkdir -p "$WORK/libs" && tar xjf "$WORK/libs.tar.bz2" -C "$WORK/libs"
SRC="$(find "$WORK/libs" -type d -name arm64-v8a | head -1)"
test -n "$SRC" || { echo "arm64-v8a introuvable dans l'archive"; find "$WORK/libs" | head -50; exit 1; }
DST="$ROOT/app/src/main/jniLibs/arm64-v8a"
rm -rf "$DST" && mkdir -p "$DST"
cp -v "$SRC"/*.so "$DST/"
test -f "$DST/libsherpa-onnx-jni.so" || { echo "libsherpa-onnx-jni.so manquante"; ls -l "$DST"; exit 1; }

echo "→ modèle $MODEL"
curl -fL --retry 4 -o "$WORK/model.tar.bz2" "$BASE/tts-models/$MODEL.tar.bz2"
tar xjf "$WORK/model.tar.bz2" -C "$WORK"
M="$WORK/$MODEL"
OUT="$ROOT/app/src/main/assets/supertonic"
rm -rf "$OUT" && mkdir -p "$OUT"
for f in duration_predictor.int8.onnx text_encoder.int8.onnx vector_estimator.int8.onnx vocoder.int8.onnx tts.json unicode_indexer.bin voice.bin; do
  test -f "$M/$f" || { echo "Fichier du modèle manquant : $f"; ls -l "$M"; exit 1; }
  cp -v "$M/$f" "$OUT/"
done
cp "$M"/LICENSE* "$OUT/" 2>/dev/null || true
du -sh "$DST" "$OUT"
