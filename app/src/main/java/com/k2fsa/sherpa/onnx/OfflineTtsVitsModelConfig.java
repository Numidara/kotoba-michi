package com.k2fsa.sherpa.onnx;
/** Miroir Java de la data class Kotlin lue par libsherpa-onnx-jni (mêmes noms et types de champs). */
public class OfflineTtsVitsModelConfig {
  public String model = "", lexicon = "", tokens = "", dataDir = "", dictDir = "";
  public float noiseScale = 0.667f, noiseScaleW = 0.8f, lengthScale = 1.0f;
}
