# Instructions du projet : iiyama Controller (Android)

## Objectif
Application Android qui configure à distance, en réseau local, un grand nombre d'écrans iiyama ProLite TF5539AS-B1AG (série TFxx39AS) : allumer/éteindre, régler la colorimétrie, activer/désactiver la télécommande. Le but est d'appliquer un même profil à une liste d'écrans sans manipulation manuelle écran par écran.

## Stack
- Kotlin, Jetpack Compose (Material 3), coroutines, architecture MVVM.
- Réseau : sockets TCP bruts (`java.net.Socket`) sur le port 5000 de chaque écran. Pas de HTTP.
- Tests : JUnit 5 + kotlinx-coroutines-test. Pas de matériel requis pour les tests unitaires.
- Permissions : `INTERNET` (et `ACCESS_NETWORK_STATE` si nécessaire). Rien d'autre sans justification.

## Règle d'or sur le protocole
La référence unique est la note « RS232/LAN commands TFxx39AS » dans `docs/`.
- Ne jamais inventer ni deviner un code de commande, une valeur de données ou un bit. Si une valeur n'est pas dans `docs/`, écrire `TODO(vérifier doc)` et poser la question.
- Chaque commande implémentée cite la section de la note dont elle vient (commentaire KDoc).
- Vecteur de test connu (commande Reset de la note) : `A6 01 00 00 00 03 01 01 A4`. Le checksum est le XOR de tous les octets précédents.

## Conventions
- Code et identifiants en anglais, commentaires et docs en français.
- La couche protocole (`protocol/`) ne dépend ni d'Android ni de l'UI : Kotlin pur, testable en JVM.
- Une issue = une PR, petite et relue. Pas de refactor hors périmètre.
- Aucune IP, aucun identifiant ni secret en dur. Les listes d'écrans sont des données saisies ou importées par l'utilisateur.
- Les erreurs réseau (timeout, refus, NACK/NAV) sont des états normaux : les remonter à l'UI par écran, ne jamais crasher ni bloquer les autres écrans.

## Structure cible
```
app/src/main/java/.../
  protocol/   trames, checksum, commandes, client TCP (Kotlin pur)
  data/       liste d'écrans, profils de configuration
  ui/         écrans Compose, ViewModels
app/src/test/ tests unitaires + faux serveur TCP
docs/         note RS232/LAN TFxx39AS (PDF) et notes de conception
```

## Définition de « terminé »
Compile, tests verts, aucune valeur de protocole non sourcée, et la PR décrit comment tester sur un vrai écran.
