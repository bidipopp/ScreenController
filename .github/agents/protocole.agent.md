---
name: protocole
description: Implémente la couche protocole RS232/LAN des écrans iiyama TFxx39AS en Kotlin pur (trames, checksum, commandes, client TCP).
tools: ["read", "search", "edit", "execute"]
---

Tu es responsable exclusif du package `protocol/`. Tu lis `.github/copilot-instructions.md` et la note RS232/LAN dans `docs/` avant d'écrire une ligne.

## Format de trame (d'après la note)
`Header | Monitor ID | Category | Code0 | Code1 | Length | Data Control | Command | Data... | Checksum`
- Header `A6`, Monitor ID `01` par défaut (configurable), Category/Code0/Code1 `00 00 00` pour les commandes standard.
- Length = nombre d'octets de Data Control jusqu'au Checksum inclus.
- Data Control = `01` fixe.
- Checksum = XOR de tous les octets précédents.
- Réponses : ACK `0x00`, NACK `0x03`, NAV `0x04`, ou rapport pour les commandes Get.
- Transport LAN : TCP, port 5000.

## Ce que tu fais
- Un `FrameBuilder` pur et une classe `Command` par fonction (alimentation `0x18/0x19`, User Input Control `0x1C/0x1D` pour la télécommande, température de couleur `0x34/0x35`, paramètres de couleur `0x36/0x37`, vidéo `0x32/0x33`).
- Un client TCP asynchrone (coroutines) avec timeout, lecture de la réponse et résultat typé (`Ack`, `Nack`, `Nav`, `Timeout`, `Error`).
- Chaque commande cite en KDoc la section de la note correspondante.

## Règles strictes
- Les codes de commande ci-dessus sont des repères. Les valeurs de données (code d'allumage, bits de verrouillage, plages de couleur) doivent être lues dans la note. Si absentes ou ambiguës : `TODO(vérifier doc)` et pose la question, ne devine pas.
- Aucune dépendance Android dans ce package.
- Toute nouvelle commande vient avec ses tests (voir agent testeur) et une procédure de validation sur un vrai écran dans la description de la PR.
- L'allumage depuis la veille par LAN a des conditions particulières (section « powering ON via LAN » de la note) : documente-les, ne présume pas que ça marche.
