# Pau'delis - Dossier de reprise pour une IA

Ce document sert de guide de continuation pour une autre IA ou un autre agent. Il explique ce que fait l'application, comment le depot est organise, ce qui est deja implante, ce qui est sensible, et comment poursuivre sans casser l'existant.

---

## 1. Objectif du projet

Pau'delis est une application Android native Kotlin pour le reseau de bus IDELIS. Elle permet de:

- consulter la carte et les arrets
- voir les lignes et leurs itineraires
- suivre les passages a l'heure ou en retard
- gerer des alertes, favoris, widgets et parametres
- proposer un tutoriel contextuel
- collecter des statistiques anonymes via Firebase Analytics si l'utilisateur accepte

Le projet est structure en `Single-Activity` avec navigation par fragments. Le point d'entree applicatif est `MainActivity`.

---

## 2. Contraintes absolues

Si tu continues ce projet, respecte ces regles sans exception:

- Ne touche pas a `index.html`. C'est une page de remerciement / donation, et son contenu ne doit pas etre modifie.
- Ne versionne pas les artefacts generes: `build/`, `app/build/`, caches, logs temporaires, APK generes, rapports jetables.
- Ne supprime pas les modifications de l'utilisateur ni les fichiers non relies a la tache.
- N'utilise jamais `git reset --hard`, `git checkout --`, ni de commande destructive sans demande explicite.
- Prefere `apply_patch` pour modifier les fichiers.
- Garde le style du projet existant: Kotlin Android classique, fragments, `ViewBinding`, `SharedPreferences`, `osmdroid`.
- Quand tu ajoutes une fonction, verifie aussi les traductions et les textes associes.
- Si tu changes un comportement visible, teste le parcours correspondant avant de conclure.

---

## 3. Etat actuel du projet

Dernier etat connu du code:

- `assembleDebug` passe.
- `lintDebug` a deja ete nettoye au maximum des warnings non bloquants connus.
- Firebase Analytics est integre avec consentement utilisateur.
- L'ecran de consentement est affiche avant le tutoriel au premier demarrage.
- Le chargement de la carte a ete durci pour reduire les gels au lancement.
- Les tickets backlog identifies comme non faits ou partiels ont ete traites autant que possible dans cette branche.

Points de vigilance encore a valider sur vrai appareil:

- temps de lancement de la carte sur telephones lents
- apparition des evenements Firebase dans `DebugView` puis dans les rapports standards
- comportement en conditions reseau degradees
- lisibilite des couleurs en theme sombre

---

## 4. Structure du depot

### 4.1 Racine

Fichiers importants:

- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `app/build.gradle`
- `app/google-services.json`
- `docs/task.md`
- `docs/RAPPORT_PAUDELIS.md`
- `index.html` ne pas modifier

### 4.2 Code applicatif

Le code principal est dans:

- `app/src/main/java/com/pau/busapp`
- `app/src/main/res`
- `app/src/main/assets`

Le dossier `Android Studio/` ressemble a un miroir local du projet. N'y fais pas de modifications par accident.

### 4.3 Fichiers de donnees et configuration

- `AppData.kt` contient une grosse base statique en memoire pour les lignes, arrets et quais.
- `app/src/main/assets/gtfs.zip` contient les donnees GTFS embarquees.
- `app/google-services.json` est la configuration Firebase du projet `pau-delis`.

---

## 5. Architecture applicative

### 5.1 Application et demarrage

- `App.kt`
  - applique le theme
  - restaure / applique le consentement analytics
  - initialise osmdroid

- `ConsentActivity.kt`
  - ecran de premier demarrage
  - demande le consentement analytics avant le tutoriel

- `MainActivity.kt`
  - activite principale
  - gestion de la barre de navigation custom
  - gestion du back
  - ouverture des fragments
  - handling des intents widget / assistant / notifications

### 5.2 Navigation et ecrans

Fragments principaux:

- `MapFragment.kt` pour la carte principale
- `LineMapFragment.kt` pour une ligne precise
- `DetailsFragment.kt` pour le detail d'un arret
- `StopListFragment.kt` pour la liste des arrets
- `LinesFragment.kt` pour la liste des lignes
- `SearchFragment.kt` pour la recherche
- `FavoritesFragment.kt` pour les favoris
- `AlertsFragment.kt` pour les alertes
- `SettingsFragment.kt` pour les preferences
- `MoreFragment.kt` pour le menu "Plus"

### 5.3 Alertes et suivi temps reel

- `AlertManager.kt`
- `AlertReceiver.kt`
- `BootReceiver.kt`
- `TrackingService.kt`
- `IdelisApi.kt`
- `PassageHelper.kt`

### 5.4 Widgets

- `StopsWidgetProvider.kt`
- `WidgetConfigActivity.kt`
- `WidgetStopConfigDialog.kt`
- `WidgetListService.kt`
- `WidgetOrderManager.kt`
- `WidgetLinesManager.kt`
- `WidgetStopConfigManager.kt`

---

## 6. Donnees, persistance et API

### 6.1 Donnees statiques

`AppData.kt` contient:

- la liste des lignes
- la liste des arrets
- les codes de quais
- les couleurs de lignes
- des correspondances et alias utilises par la recherche et les widgets

Important:

- ce fichier est tres volumineux
- il faut eviter les refactors inutiles
- toute modification dedans peut avoir des impacts de performance ou de compatibilite

### 6.2 Persistance locale

L'app s'appuie surtout sur:

- `SharedPreferences`
- serialisation JSON
- objets managers specialises

Les donnees locales a conserver:

- favoris
- alertes
- configuration widgets
- theme
- langue
- style de navigation
- consentement analytics

### 6.3 API temps reel IDELIS

Le projet utilise une couche reseau personnalisee pour interroger IDELIS.

Points importants:

- le token API est injecte via `BuildConfig.IDELIS_API_KEY`
- la requete peut passer par HTTPS manuel / sockets SSL selon les parties du code
- il faut gerer proprement:
  - `404`
  - `5xx`
  - timeout
  - perte de connexion
  - reponse vide ou partielle

Les statuts de passages sont calcules via:

- `PassageHelper.kt`
- `IdelisApi.kt`

Ne casse pas le mapping:

- `THEORIQUE`
- `A_LHEURE`
- `RETARD`
- `AVANCE`
- `ANNULE`

---

## 7. Consentement et Firebase Analytics

### 7.1 Situation actuelle

Firebase Analytics est integre pour suivre de facon anonyme:

- changements d'onglets
- clics sur les gros boutons
- ouvertures d'ecran
- recherches
- ouvertures d'arrets / lignes / favoris

Le consentement est gere explicitement:

- premiere ouverture: `ConsentActivity`
- choix stocke localement
- consentement reapplique au demarrage

### 7.2 Ce qu'il faut retenir

- sans consentement, aucune collecte analytics ne doit partir
- avec consentement, les evenements doivent pouvoir remonter
- pour tester tout de suite, il faut utiliser `DebugView`
- les rapports standards Firebase peuvent prendre du temps

### 7.3 Commande de debug utile

Pour forcer le debug analytics sur un appareil de dev:

```bash
adb shell setprop debug.firebase.analytics.app com.pau.busapp
```

Pour desactiver:

```bash
adb shell setprop debug.firebase.analytics.app .none
```

### 7.4 Regle importante

N'envoie pas de donnees personnelles. Garde uniquement:

- nom d'ecran
- nom d'onglet
- nom d'element clique
- action

Ne trace pas:

- position GPS brute de l'utilisateur
- donnees nominatives
- identifiants externes sensibles

---

## 8. Performance et risques connus

### 8.1 Carte principale

Le point le plus sensible est `MapFragment`.

Raison:

- beaucoup de markers
- bitmap de markers
- chargement de tuiles
- overlays multiples
- donnees statiques tres volumineuses

La carte a deja ete durcie pour eviter un blocage au demarrage. Si tu reviens sur cette partie:

- garde l'ouverture rapide
- evite de construire trop d'objets sur le thread UI
- teste sur un appareil reel, pas seulement sur emulateur

### 8.2 Lancements lents ou ANR

Si l'app semble "ne repond pas":

- verifier `MainActivity`
- verifier `MapFragment`
- verifier la creation des markers
- verifier les chargements synchrones
- verifier les appels reseau sur le thread principal
- verifier les gros traitements JSON / GTFS / bitmap

### 8.3 Requetes reseau

Toute lecture reseau doit:

- etre asynchrone
- avoir un timeout raisonnable
- gerer les erreurs sans crasher
- avoir un fallback visuel clair

---

## 9. Etat du backlog Jira

Le tableau Jira interne a deja ete largement traite. Si tu reprends le projet:

- considere le rapport `docs/RAPPORT_PAUDELIS.md` comme la source de suivi des tickets
- si un ticket est marque `fait`, ne le rouvre pas sans raison
- si une capture CE est attendue, utilise seulement les captures deja disponibles dans le dossier de travail
- ne produis pas d'image ou de video nouvelle si la consigne dit d'examiner seulement l'existant

### 9.1 Tickets deja couverts dans le dernier cycle

Les sujets principaux deja traites comprennent:

- traduction et couverture des langues
- alertes et notifications
- tri et recherche
- bandeaux d'information trafic
- calcul des statuts de passages
- tracage de lignes via shapes GTFS
- affichage des terminus sur la carte
- correction du theme sombre et de la lisibilite
- analytics Firebase avec consentement
- durcissement de l'affichage / crash ponctuel

### 9.2 Si tu dois ajouter un nouveau ticket

Ajoute-le dans le rapport et dans le suivi Jira avec:

- ID
- type
- description
- fichiers impactes
- statut
- plan d'action
- test attendu

---

## 10. Guide de continuation pour une autre IA

Si tu reprends le travail, suis cet ordre:

1. Lire ce document.
2. Lire `docs/RAPPORT_PAUDELIS.md` pour voir l'etat exact des tickets.
3. Verifier le statut Git avec `git status`.
4. Identifier uniquement les fichiers source et docs a modifier.
5. Faire une petite modification ciblee.
6. Lancer au minimum:
   - `./gradlew assembleDebug`
   - `./gradlew lintDebug`
7. Nettoyer les artefacts generes avant commit si besoin.
8. Committer avec un message precis.
9. Pousser la branche de travail.

### 10.1 Ce qu'il faut privilegier

- corrections simples et robustes
- lecture claire du code
- tests reproductibles
- documentation synchronisee
- compatibilite avec l'existant

### 10.2 Ce qu'il faut eviter

- refactor global sans besoin
- suppression de donnees historiques
- changement du comportement de `index.html`
- ajout de dependances lourdes sans raison
- multiplication des couches abstraites

---

## 11. Verification manuelle recommandee

Avant de declarer un travail termine, verifier:

- ouverture de l'application
- affichage de la carte
- navigation entre onglets
- consultation d'un arret
- consultation d'une ligne
- recherche
- favoris
- alertes
- widget
- consentement analytics
- rapports Firebase / DebugView si concerne

Sur Android reel, tester au moins:

- theme clair
- theme sombre
- reseau faible
- premier demarrage
- retour apres mise en arriere-plan

---

## 12. Referentiels utiles

- `MainActivity.kt` pour la navigation et les intents
- `MapFragment.kt` pour la carte et les performances
- `DetailsFragment.kt` pour les passages et la presentation
- `IdelisApi.kt` pour le temps reel
- `PassageHelper.kt` pour les statuts
- `AnalyticsTracker.kt` pour Firebase
- `ConsentActivity.kt` pour le consentement
- `SettingsFragment.kt` pour les options utilisateur
- `docs/RAPPORT_PAUDELIS.md` pour le suivi projet

---

## 13. Message court a retenir

Pau'delis est une app Android complexe mais deja bien avancee. La prochaine IA doit travailler avec prudence, sans toucher a `index.html`, en gardant les tests verts, en surveillant la carte et le demarrage, et en tenant les documents de suivi a jour.
