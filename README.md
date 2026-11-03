# Kotoba Michi 言葉道

Application Android de leçons audio de japonais selon la méthode Pimsleur, pensée pour la voiture :
on lance une leçon avant de partir, et tout se fait à l'oreille.

- **Voix embarquée** : Supertonic 3 (Supertone) exécuté par [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx),
  entièrement hors ligne. Un seul modèle parle français et japonais, avec 10 voix.
- **Lecture en arrière-plan** : service de premier plan, écran éteint, commandes depuis la notification,
  le volant ou un casque Bluetooth, pause automatique pendant les annonces du GPS.
- **Méthode** : écoute du dialogue, reconstruction depuis la fin, anticipation, rappels à intervalles croissants
  (10 s, 30 s, 2 min, 5 min 30, 12 min, 22 min), révisions des leçons passées, jeu de rôle et écoute finale.
- **Leçons** : 8 leçons de base (niveaux 1 à 3) et génération de nouvelles leçons thématiques par Claude
  (niveaux 1 à 6) avec ta clé API Anthropic, chiffrée par le Keystore du téléphone.

## Installer

Chaque envoi sur `main` lance le workflow **Build APK**. Il publie une version dans l'onglet *Releases*
(`build-N`) : ouvre-la sur le téléphone, touche `kotoba-michi-N.apk`, autorise l'installation depuis
le navigateur si Android le demande. Les builds suivants s'installent par-dessus et gardent la progression.

Le job **voix** joint un artefact `extraits-voix` : les 10 voix en français et en japonais, et un rapport
sur les kanji que le modèle sait lire.

## Compiler soi-même

```bash
./scripts/fetch-deps.sh        # bibliothèques sherpa-onnx v2.0.1 et modèle Supertonic 3
./gradlew assembleRelease      # JDK 17 et SDK Android 35
```

## Signature

Par défaut, l'APK est signé avec `keystore/kotoba-michi.jks` (mot de passe `kotoba-michi`), ce qui suffit
pour un dépôt privé et un usage personnel. Pour un trousseau tenu hors du dépôt, ajoute les secrets
`KM_KEYSTORE_B64` (le .jks en base64), `KM_KEYSTORE_PASSWORD` et `KM_KEY_PASSWORD` : le workflow les
utilise en priorité. Attention : changer de trousseau oblige à désinstaller l'ancienne version.

## Structure

| Fichier | Rôle |
| --- | --- |
| `Session.java` | Construit le déroulé d'une séance (planificateur à rappels espacés) |
| `Kana.java` | Découpage en mores, reconstruction depuis la fin |
| `Voice.java` | Moteur Supertonic, choix kanji ou kana, cache audio |
| `PlayerService.java` | Lecture en service de premier plan, MediaSession, focus audio |
| `Generator.java` | Écriture de nouvelles leçons par l'API Anthropic |
| `com/k2fsa/sherpa/onnx/*` | Liaison Java vers `libsherpa-onnx-jni.so` (mêmes champs que l'API Kotlin officielle v2.0.1) |

## Licence

Le code de Kotoba Michi est publié sous licence MIT (voir [`LICENSE`](LICENSE)), © 2026 Stella Caltagirone.

Composants tiers, téléchargés au moment de la compilation et non couverts par cette licence :
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) et ONNX Runtime : licence Apache 2.0 ;
  les classes de `com/k2fsa/sherpa/onnx/` reprennent l'interface de son API Kotlin ;
- modèle de voix Supertonic 3 (Supertone) : sa propre licence, jointe dans
  `app/src/main/assets/supertonic/` après `fetch-deps.sh`.
