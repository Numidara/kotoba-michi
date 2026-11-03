#!/usr/bin/env python3
"""Contrôle du modèle sur l'ordinateur de build : couverture des caractères des leçons
et extraits audio (français et japonais) pour écouter les 10 voix avant d'installer l'APK."""
import json, sys, unicodedata, pathlib, struct
import numpy as np
import sherpa_onnx, soundfile as sf

root = pathlib.Path(__file__).resolve().parent.parent
m = root / "app/src/main/assets/supertonic"
out = root / "voice-samples"; out.mkdir(exist_ok=True)

idx = np.fromfile(m / "unicode_indexer.bin", dtype="<i4")
def covered(t):
    for ch in unicodedata.normalize("NFKD", t):
        if ch.isspace(): continue
        if ord(ch) >= len(idx) or idx[ord(ch)] < 0: return False
    return True

lessons = json.loads((root / "app/src/main/assets/lessons/foundation.json").read_text())
texts = [x["jp"] for l in lessons for x in l["items"] + l["dialogue"]]
miss = sorted({ch for t in texts for ch in unicodedata.normalize("NFKD", t) if not ch.isspace() and (ord(ch) >= len(idx) or idx[ord(ch)] < 0)})
ok = sum(covered(t) for t in texts)
print(f"Phrases japonaises lisibles en kanji : {ok}/{len(texts)}")
print("Caractères inconnus du modèle :", "".join(miss) or "aucun")

cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    supertonic=sherpa_onnx.OfflineTtsSupertonicModelConfig(
        duration_predictor=str(m / "duration_predictor.int8.onnx"), text_encoder=str(m / "text_encoder.int8.onnx"),
        vector_estimator=str(m / "vector_estimator.int8.onnx"), vocoder=str(m / "vocoder.int8.onnx"),
        tts_json=str(m / "tts.json"), unicode_indexer=str(m / "unicode_indexer.bin"), voice_style=str(m / "voice.bin")),
    num_threads=2, provider="cpu"))
tts = sherpa_onnx.OfflineTts(cfg)
print("Voix :", tts.num_speakers, "· fréquence :", tts.sample_rate)
samples = {"fr": "Voici comment on dit : je comprends un peu le japonais.", "ja": "日本語がちょっと分かります。よろしくお願いします。", "ja-kana": "にほんごが ちょっと わかります"}
for sid in range(tts.num_speakers):
    for name, text in samples.items():
        g = sherpa_onnx.GenerationConfig(); g.sid = sid; g.num_steps = 5; g.speed = 1.0
        g.extra = {"lang": "fr" if name == "fr" else "ja"}
        a = tts.generate(text, g)
        sf.write(out / f"voix{sid+1:02d}-{name}.wav", a.samples, a.sample_rate, subtype="PCM_16")
print("Extraits écrits dans", out)
