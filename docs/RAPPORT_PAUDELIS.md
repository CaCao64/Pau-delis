# Rapport d'exploration de Pau'delis

Date d'exploration : 13 aout 2026

## 1. Synthese generale

Pau'delis est une application Android native en Kotlin, centre sur l'affichage des lignes IDELIS de Pau, la consultation des arrets, le suivi des passages, les alertes, les favoris, les widgets et une forte couche de personnalisation.

Le projet est structure autour d'une architecture `Single-Activity` avec navigation par fragments. Le coeur fonctionnel est deja bien avance et couvre :

- Carte OpenStreetMap via `osmdroid`
- Consultation des arrets et des lignes
- Recherche avec historique
- Favoris arrets, lignes et bus a l'arret
- Alertes planifiees avec `AlarmManager`
- Suivi en foreground service avec notifications persistantes
- Widgets d'accueil configurables
- Parametres d'interface, langue, theme, style de navigation et accessibilite

Le depot contient aussi plusieurs artefacts non applicatifs :

- `index.html`, qui ressemble a une page de remerciement / soutien avec Google Analytics
- `logs/hs_err_pid18080.log`, journal de crash JVM
- `logs/replay_pid18080.log`, fichier vide de relecture
- Un dossier `Android Studio/` qui ressemble a une copie miroir du projet
- Le dossier `build/` et `app/build/`, qui contiennent les artefacts generes

## 2. Inventaire du depot

### 2.1. Racine du projet

Fichiers principaux a la racine :

- `settings.gradle`
- `build.gradle`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`
- `app/build.gradle`
- `app/src/main/AndroidManifest.xml`
- `docs/task.md`
- `index.html`

### 2.2. Structure applicative principale

Le code utile vit dans :

- `app/src/main/java/com/pau/busapp`
- `app/src/main/res`
- `app/src/main/assets`

Comptages observes :

- 45 fichiers Kotlin dans `app/src/main/java/com/pau/busapp`
- 90 fichiers XML sous `app/src/main`
- 102 fichiers dans `app/src/main/res`
- 18 dossiers de ressources de type `values*` dans `app/src/main/res`

### 2.3. Dossier miroir "Android Studio"

Le dossier `Android Studio/` reprend quasiment la meme structure que `app/`, avec les memes classes, les memes layouts et les memes ressources de base.

Interprétation probable :

- copie de travail
- sauvegarde locale
- ou projet duplique pour experimentation

En pratique, cela complique la maintenance car il existe deux arbres tres proches dans le meme depot.

## 3. Configuration de build

### 3.1. Configuration globale

Le projet est nomme `BusPau` dans `settings.gradle`, avec un unique module `:app`.

Le `build.gradle` racine utilise :

- Android Gradle Plugin `8.13.2`
- Kotlin `1.9.10`

### 3.2. Configuration du module `app`

Points importants observes dans `app/build.gradle` :

- `namespace` et `applicationId` : `com.pau.busapp`
- `compileSdk` : 34
- `minSdk` : 24
- `targetSdk` : 34
- `versionCode` : 9
- `versionName` : `1.8`
- Java/Kotlin cible : 11
- `viewBinding` active
- `buildConfig` active
- `coreLibraryDesugaring` active

La cle API IDELIS est injectee via :

- `BuildConfig.IDELIS_API_KEY`

Dependances principales :

- `androidx.core:core-ktx`
- `androidx.appcompat:appcompat`
- `com.google.android.material:material`
- `androidx.fragment:fragment-ktx`
- `org.osmdroid:osmdroid-android`
- `com.squareup.okhttp3:okhttp`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`
- `com.android.tools:desugar_jdk_libs`

Observation utile :

- l'application utilise du HTTPS manuel via sockets SSL dans le code, donc la presence d'OkHttp semble plutot preventive ou partiellement inutile actuellement

## 4. Manifest, permissions et composants Android

Le manifeste declare :

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM`
- `USE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `WRITE_EXTERNAL_STORAGE` limite a `maxSdkVersion=28`

L'application autorise aussi :

- `android:usesCleartextTraffic="true"`

Composants declares :

- `MainActivity`
- `WidgetConfigActivity`
- `TrackingService`
- `WidgetListService`
- `AlertReceiver`
- `StopsWidgetProvider`
- `BootReceiver`

## 5. Architecture technique

### 5.1. Modele general

Le projet suit une logique claire :

- une activite principale unique
- plusieurs fragments specialises
- stockage local via `SharedPreferences`
- donnees transport via une base statique Kotlin et un fichier GTFS offline
- temps reel via l'API IDELIS
- widgets et services Android pour la persistance et le suivi

### 5.2. Application et initialisation

`App.kt` :

- applique le theme sauvegarde avant affichage
- configure le cache `osmdroid`
- cree les dossiers de cache cartes et tuiles dans le cache applicatif

Cela montre que la carte est un element central du produit.

### 5.3. Navigation et UI globale

`MainActivity.kt` est le point d'entree principal :

- barre de navigation personnalisee
- onglets dynamiques
- gestion du bouton retour avec `OnBackPressedCallback`
- animation de transition
- ouverture des fragments selon les actions utilisateur
- gestion des deep links venant des widgets et notifications
- bandeaux d'etat API sur plusieurs ecrans
- appel au tutoriel contextuel si l'utilisateur ne l'a pas termine

## 6. Donnees metier et persistance

### 6.1. Donnees statiques du reseau

`AppData.kt` contient :

- `BusStop`
- `BusLine`
- `LineType`
- de nombreuses lignes IDELIS et leurs arrets
- les troncons par direction
- une base statique en memoire tres volumineuse

Le fichier est tres gros :

- environ 130 Ko

Fonctionnellement, il sert de referentiel local pour :

- la liste des lignes
- la liste des arrets
- les relations arret <-> ligne
- la navigation carte / ligne / arret

### 6.2. Alertes

`Alert.kt` et `AlertManager.kt` gerent :

- un modele d'alerte riche
- recurrence quotidienne / hebdomadaire / dates specifiques
- jours de semaine
- dates exclues
- sauvegarde JSON dans `SharedPreferences`
- planification via `AlarmManager`
- annulation des alertes
- nettoyage des alertes "today" passees
- gestion des vacances scolaires et jours feries

Le modele `Alert` contient notamment :

- `stopName`
- `lineName`
- `destination`
- `hourMinute`
- `minutesBefore`
- `conditions`
- `weekdays`
- `specificDates`
- `excludedDates`
- `enabled`

### 6.3. Favoris

`FavoritesManager.kt` gère :

- favoris d'arrets
- favoris de lignes
- favoris de bus a l'arret
- ordre de tri des favoris
- ensemble widget limite
- arret par defaut

Particularite importante :

- le projet ne centralise pas tout dans un seul `app_prefs`, mais dans plusieurs espaces de preferences specialises

### 6.4. Historique de recherche

`SearchHistoryManager.kt` :

- stocke les requetes recentes
- limite l'historique a 20 entrees
- supprime les doublons en gardant la plus recente

## 7. Donnees transport hors ligne

### 7.1. GTFS offline

`app/src/main/assets/gtfs.zip` est un element important du projet.

`GtfsReader.kt` :

- ouvre `gtfs.zip`
- lit `routes.txt`, `trips.txt`, `calendar.txt`, `calendar_dates.txt`, `stop_times.txt`
- calcule les passages theoriques
- gere un cache en memoire
- calcule les services actifs selon la date

Cette couche permet a l'application de rester exploitable hors connexion ou en fallback si l'API IDELIS ne repond pas.

### 7.2. Calcul des horaires

`PassageHelper.kt` :

- parse des formats comme `3 min`, `Imminent`, `14:32`
- calcule l'ecart entre un passage reel et un horaire theorique
- mappe l'ecart vers un statut :
  - a l'heure
  - retard
  - avance
  - theorique

Cela sert directement :

- aux details d'arret
- aux alertes
- au suivi continu
- aux widgets

## 8. Couche reseau et temps reel

### 8.1. API IDELIS

`IdelisApi.kt` communique avec :

- `https://api.idelis.fr/GetStopMonitoring`

Le code n'utilise pas une abstraction HTTP classique ici :

- ouverture directe d'un socket SSL
- requete HTTP manuelle
- decodage manuel du chunked transfer encoding
- parsing JSON en sortie

Cette approche est coherente avec `docs/task.md`, qui parle de sockets SSL et d'une dependance legere.

### 8.2. Gestion des statuts

Le code reconnait :

- `THEORIQUE`
- `A_LHEURE`
- `RETARD`
- `AVANCE`
- `ANNULE`

`TrackingService.kt`, `AlertsFragment.kt` et `DetailsFragment.kt` s'appuient dessus pour afficher des icones, des couleurs et des statuts textuels.

### 8.3. Gestion des erreurs

Le projet contient deja de la logique pour :

- detecter les erreurs reseau
- detecter les erreurs HTTP
- proposer un fallback GTFS
- afficher des messages "API indisponible" ou "connexion perdue"

## 9. Ecrans et navigation

### 9.1. Carte

`MapFragment.kt` :

- affiche une carte `osmdroid`
- gere la position utilisateur
- gere l'emulation GPS
- affiche les arrets proches
- affiche un loader lors de la recherche du plus proche arret
- gère un overlay du point de localisation
- gère un overlay des arrets proches
- recharge les arrets selon le zoom

Le code montre aussi :

- une tuile sombre Stadia Maps en mode nuit
- un bouton emulation masquable depuis les parametres

### 9.2. Fiche arret

`DetailsFragment.kt` :

- affiche le nom de l'arret
- affiche les lignes desservies
- applique un degrade de couleur base sur les lignes
- affiche les passages temps reel ou theoriques
- conserve la position de scroll
- permet d'aller vers la ligne
- permet de localiser l'arret sur la carte
- permet d'ajouter une alerte
- permet de toggle favoris

### 9.3. Liste des arrets

`StopListFragment.kt` :

- recherche texte
- normalisation des accents avec `Normalizer`
- tri par nom
- tri par distance GPS
- tri par prochain passage
- maintien de la position lors du refresh
- affichage des passages par arret

### 9.4. Recherche

`SearchFragment.kt` :

- recherche parmi les lignes et les arrets
- historique recent
- suppression d'entrees de l'historique
- recherche insensible aux accents
- ouverture directe de la ligne ou de l'arret depuis un resultat

### 9.5. Favoris

`FavoritesFragment.kt` :

- favoris arrets
- favoris lignes
- favoris bus a l'arret
- ordre personnalisable
- sections pliees / depliees pour les alertes et le rendu
- refresh automatique si l'heure courante correspond a la vue active

### 9.6. Lignes

`LinesFragment.kt` :

- liste les lignes du reseau
- affiche badges colorees
- remplace certaines lignes speciales par icones dediees
- ouvre le detail de ligne au clic

### 9.7. Carte de ligne

`LineMapFragment.kt` :

- dessine les polylines des deux sens
- affiche les arrets comme marqueurs
- identifie les terminus
- centre la carte sur l'ensemble de la ligne
- supprime les info windows vides

### 9.8. Ecran "Plus"

`MoreFragment.kt` :

- regroupe les ecrans secondaires
- propose un acces au tutoriel
- propose un bouton de don
- met en valeur certains textes avec une animation visuelle

## 10. Background, alertes et notifications

### 10.1. Foreground service

`TrackingService.kt` :

- lance un suivi actif au premier plan
- met a jour la notification periodiquement
- ajoute des actions pour suspendre / reprendre / rafraichir
- passe sur un mode "bus passe" quand l'heure est depassee
- utilise un fallback GTFS si l'API ne repond pas

### 10.2. Recepteurs systeme

`AlertReceiver.kt` :

- recoit les alarmes
- cree le channel de notification
- lance `TrackingService`
- gere aussi un toggle du suivi depuis la notification

`BootReceiver.kt` :

- remet probablement en place les alertes au demarrage

### 10.3. Alertes dans l'UI

`AlertsFragment.kt` :

- charge les alertes depuis `AlertManager`
- regroupe par nom d'arret
- permet de plier / deplier les groupes
- affiche les passages theoriques et reels
- permet d'activer / desactiver
- permet d'editer
- permet de supprimer

### 10.4. Ajout / edition

`AddAlertDialog.kt` :

- choix d'arret
- choix de ligne et de direction
- choix de l'heure
- recurrence par jours / semaines / dates
- gestion des dates exclues
- compatibilite avec l'edition

## 11. Widgets d'accueil

Les widgets sont une vraie sous-application dans le projet.

### 11.1. Provider principal

`StopsWidgetProvider.kt` :

- gere l'update du widget
- controle l'auto refresh
- applique un theme clair / sombre
- gere l'opacite
- utilise `RemoteViews`
- cree un adapter de liste avec `WidgetListService`
- expose un bouton de rafraichissement
- limite la frequence de mise a jour

### 11.2. Configuration widget

`WidgetConfigActivity.kt` :

- permet de choisir theme du widget
- permet de regler l'opacite
- enregistre les preferences par widget ID

### 11.3. Ordre et contenu du widget

`WidgetStopsFragment.kt` :

- permet de reordonner les entrees par drag and drop
- active / desactive les elements
- ajoute des lignes au widget
- gère les bus favoris et les arrets favoris
- sauvegarde l'ordre final

### 11.4. Gestionnaires associes

`WidgetOrderManager.kt` :

- stocke l'ordre global
- stocke les elements actifs
- migre les anciennes entrees sans prefixe

`WidgetLinesManager.kt` :

- gere les lignes du widget
- limite la liste a 3 lignes

`WidgetStopConfigManager.kt` :

- stocke la configuration par arret
- supprime les arrets expirés
- verifie la compatibilite avec les jours / vacances

`WidgetListService.kt` :

- fournit les vues de liste du widget
- applique les couleurs selon le theme
- cree les fill-in intents vers l'app

`WidgetStopConfigDialog.kt` et `WidgetStopConfig.kt` :

- gerent la configuration fine d'une entree de widget

## 12. Parametres, personnalisation et accessibilite

`SettingsFragment.kt` concentre une grosse partie de la personnalisation :

- changement de langue
- changement de theme
- changement du style de navigation
- configuration de la barre du bas
- configuration des widgets
- export / import JSON
- activation / desactivation du bouton d'emulation sur la carte

Autres gestionnaires :

- `ThemeManager.kt`
- `LocaleHelper.kt`
- `ColorblindManager.kt`
- `NavStyleManager.kt`
- `NavConfigManager.kt`

### 12.1. Langues

Le projet contient des dossiers :

- `values-fr`
- `values-en`
- `values-es`
- `values-de`
- `values-it`
- `values-ja`
- `values-ko`
- `values-pt`
- `values-pl`
- `values-ru`
- `values-tr`
- `values-zh`
- `values-ar`
- `values-hi`
- `values-nl`

Cela montre une couverture de traduction bien plus large que ce que la spec laisse entendre.

### 12.2. Theme et navigation

`NavStyleManager` propose plusieurs styles :

- pill
- indicateur
- labels actifs
- compact
- flottant

`NavConfigManager` permet de definir :

- l'ordre
- les onglets visibles
- une limite de 4 onglets visibles

### 12.3. Accessibilite

La prise en charge colorimetrie / contraste est presente via :

- `ColorblindManager`

Et l'overlay de tutoriel met l'accent sur les couleurs et les interactions.

## 13. Tutoriel et onboarding

`TutorialOverlay.kt` :

- construit un overlay plein ecran
- bloque les clics derriere
- affiche des etapes progressive
- gere un swipe gauche / droite
- met en forme certains mots en couleur
- ajoute une progression visuelle

`TutorialManager.kt` :

- semble simplement gerer l'etat "tutoriel termine"

Le tutoriel est donc une vraie couche d'onboarding, pas juste un pop-up ponctuel.

## 14. Fichiers annexes et assets

### 14.1. Ressources

Le dossier `app/src/main/res` est riche :

- beaucoup de `drawable`
- beaucoup de layouts pour les fragments et widgets
- plusieurs animations
- un ensemble d'icones de navigation et d'etat

### 14.2. Assets

`app/src/main/assets/gtfs.zip` est le seul asset fonctionnel observe et il est essentiel.

### 14.3. Page HTML

`index.html` n'est pas le code Android. C'est une page de remerciement / soutien avec :

- Google Analytics
- detection de bots
- design responsive
- style orienté marque

Elle semble plutot liee a la communication ou a un support externe.

## 15. Analyse de `docs/task.md`

### 15.1. Nature du document

`docs/task.md` est un document de specification technique et d'architecture pour Pau'delis, nomme "BusV6".

Il couvre :

- identite technique
- donnees et persistance
- reseau
- services d'arriere-plan
- UI
- widgets
- tutoriel
- tableau de bord Jira

### 15.1.1. Tableau de Bord Integral des 45 Tickets Jira (`BUS`)

#### Backlog a faire

| Ticket | Type | Demande / Description Jira | CE (Attente Capture) | Composant Code Impacte | Statut | Plan d'Action Technique |
| :---: | :---: | :--- | :---: | :--- | :---: | :--- |
| **BUS-3** | Bug | Traduction incomplete de l'application (EN / ES) | Non | `strings.xml` (`values-en`, `values-es`) | En cours | Traduction integrale de tous les composants de l'application et du tutoriel. |
| **BUS-16** | Story | Gestion des 2 directions pour un meme arret dans les notifs | Non | `AddAlertDialog.kt`, `Alert.kt` | A faire | Stocker la destination explicite dans le modele `Alert` pour cibler la bonne direction. |
| **BUS-17** | Story | Ecrire "Bus passe" au lieu de l'horaire perime dans la notif | Non | `TrackingService.kt` | A faire | Mettre a jour le texte de la notification avec `R.string.bus_passed` au franchissement de l'heure. |
| **BUS-18** | Story | Clic sur notification -> Defilement auto jusqu'a l'arret | Non | `MainActivity.kt`, `StopListFragment.kt` | A faire | Transmettre l'ID de l'arret dans le `PendingIntent` de notification et scroller la liste. |
| **BUS-19** | Story | Ranger les alertes par nom d'arret (menus deroulants) | Non | `AlertsFragment.kt` | A faire | Implementation d'un RecyclerView a en-tetes extensibles (Expandable Grouping). |
| **BUS-22** | Bug | Notification qui disparait lors d'une perte de connexion | Non | `TrackingService.kt` | A faire | Conserver la notification en indiquant "Connexion perdue..." sans appeler `cancel()`. |
| **BUS-24** | Story | Trier les arrets par prochain passage | Non | `StopListFragment.kt` | A faire | Ajouter un comparateur sur les minutes restantes du prochain bus. |
| **BUS-25** | Story | Clic sur notification -> Defilement auto jusqu'a la ligne | Non | `MainActivity.kt`, `DetailsFragment.kt` | A faire | Transmettre l'ID de la ligne via l'Intent de la notif et scroller le RecyclerView. |
| **BUS-26** | Bug | Crash au clic sur "Organiser le widget" | Non | `WidgetConfigActivity.kt`, `WidgetOrderManager.kt` | A faire | Fix `NullPointerException` dans l'Intent ou la liste d'ordonnancement du widget. |
| **BUS-27** | Story | Moderniser le widget | Non | `StopsWidgetProvider.kt`, `res/layout/widget_*` | A faire | Redesign des RemoteViews avec coins arrondis et style Material You. |
| **BUS-28** | Bug | Bug general d'affichage / crash ponctuel | Non | Plusieurs fragments | A faire | Diagnostic et correction selon les logs de crash. |
| **BUS-29** | Story | Rechargement auto des horaires au retour sur l'app | Non | `MainActivity.kt`, `DetailsFragment.kt` | A faire | Declencher le rafraichissement dans la methode `onResume()` des fragments actifs. |
| **BUS-30** | Bug | Consommation batterie excessive (8% pour 5min d'ecran) | Non | `TrackingService.kt`, `MapFragment.kt` | A faire | Reduire la frequence de rafraichissement GPS et suspendre les coroutines inactives. |
| **BUS-31** | Story | Periode vacances scolaires | Non | `Alert.kt`, `AlertManager.kt` | A faire | Integration du calendrier des vacances pour desactiver automatiquement les alertes `NO_SCHOOL_HOLIDAYS`. |
| **BUS-32** | Bug | Oblige de mettre les accents dans la recherche | Non | `SearchFragment.kt`, `StopListFragment.kt` | A faire | Normaliser les chaines avec `java.text.Normalizer` pour ignorer les diacritiques. |
| **BUS-33** | Bug | En ligne alors que API temporairement indisponible (doublon) | Non | `IdelisApi.kt` | A faire | Fusionner avec le traitement du ticket BUS-34. |
| **BUS-34** | Bug | En ligne alors que l'API est temporairement indisponible | Non | `IdelisApi.kt`, `DetailsFragment.kt` | A faire | Gestion d'erreur HTTP 5xx/SocketTimeout avec bandeau "API indisponible" explicite. |
| **BUS-35** | Bug | Arrets du FeBus (F) en orange alors qu'il n'y passe pas | Non | `AppData.kt`, `MapFragment.kt` | A faire | Correction du filtrage des lignes associees aux marqueurs d'arrets du FeBus. |
| **BUS-36** | Story | Ajouter un toast quand alerte ajoutee | Non | `AddAlertDialog.kt`, `AlertsFragment.kt` | A faire | Affichage d'un `Toast.makeText(context, R.string.alert_added, Toast.LENGTH_SHORT).show()`. |
| **BUS-37** | Story | Ajouter bandeau d'information en ligne CE | Oui | `LinesFragment.kt`, `LineDetailFragment.kt` | A faire | Composant Marquee / Banner affichant les perturbations du reseau envoyees par l'API. |
| **BUS-38** | Story | Degrade en haut de l'arret avec les couleurs des bus y passant | Non | `DetailsFragment.kt` | A faire | Generation dynamique d'un `GradientDrawable` base sur les couleurs des badges de ligne. |
| **BUS-39** | Bug | Marque en avance alors qu'en retard de 7 min. CE | Oui | `PassageHelper.kt`, `IdelisApi.kt` | A faire | Corriger le calcul de l'ecart `ecartMin` et le mapping de l'enum `PassageStatut`. |
| **BUS-40** | Bug | Le bus ne passe pas sur la route CE | Oui | `LineMapFragment.kt`, `AppData.kt` | A faire | Corriger les coordonnees GPS du trace de ligne pour coller au reseau routier OSM. |
| **BUS-41** | Story | Ajouter le nom des terminus sur la carte de l'itineraire. CE | Oui | `LineMapFragment.kt` | A faire | Ajouter des marqueurs textuels aux coordonnees de depart et d'arrivee du trace `Polyline`. |
| **BUS-42** | Bug | Quand bus en retard, horloge en noir et donc illisible pour le theme sombre. Solution : la mettre en orange. CE | Oui | `PassageHelper.kt`, `DetailsFragment.kt` | A faire | Passer la couleur de l'horloge / icone de retard en orange adaptatif au theme sombre. |
| **BUS-43** | Story | Option de commander l'app avec l'Assistant Google ? | Non | `AndroidManifest.xml`, `actions.xml` | A faire | Ajout des App Actions / Shortcuts Google Assistant pour les commandes vocales. |
| **BUS-44** | Bug | Widget actif sans l'etre (batterie) + faux statut hors-ligne | Non | `StopsWidgetProvider.kt`, `WidgetListService.kt` | A faire | Ajuster la frequence d'actualisation du widget et corriger le check reseau. |
| **BUS-45** | Story | Google Analytics sur les onglets | Non | `MainActivity.kt` | A faire | Integration de Firebase Analytics / Google Analytics pour tracer les changements d'onglets. |
| **BUS-46** | Story | Animation et texte "Recherche d'arrets les plus proches en cours" au clic bouton | Non | `MapFragment.kt`, `StopListFragment.kt` | A faire | Afficher un loader / dialog avec message de recherche active lors du clic sur la localisation. |

#### Tickets termines

| Ticket | Type | Titre / Fonctionnalite Realisee | CE (Attente Capture) | Composant Implemente | Statut |
| :---: | :---: | :--- | :---: | :--- | :---: |
| **BUS-1** | Story | Section "A propos" avec credits developpeur | Non | `MoreFragment.kt` | Termine |
| **BUS-2** | Bug | Conservation du defilement lors du rafraichissement | Non | `DetailsFragment.kt` | Termine |
| **BUS-4** | Bug | Blocage des clics sur l'application pendant le tutoriel | Non | `TutorialOverlay.kt` (`isClickable = true`) | Termine |
| **BUS-5** | Bug | Correction des bugs de destination dans la liste des arrets | Non | `StopListFragment.kt` | Termine |
| **BUS-6** | Story | Prise en charge du bouton Retour (Back) physique du telephone | Non | `MainActivity.kt` (`onBackPressedDispatcher`) | Termine |
| **BUS-7** | Story | Masquer le bouton "Emuler ici" et l'ajouter en option parametre | Non | `MapFragment.kt`, `SettingsFragment.kt` | Termine |
| **BUS-8** | Story | Voir le trace de la ligne sur la carte depuis l'arret | Non | `DetailsFragment.kt`, `LineMapFragment.kt` | Termine |
| **BUS-9** | Story | Redirection vers la ligne au clic sur le badge | Non | `DetailsFragment.kt` | Termine |
| **BUS-10** | Story | Tutoriel contextuel affiche par page | Non | `MainActivity.kt`, `TutorialOverlay.kt` | Termine |
| **BUS-11** | Bug | Desactivation de la bulle d'info vide sur le trace polyline | Non | `LineMapFragment.kt` | Termine |
| **BUS-12** | Bug | Suppression de l'epingle inactive dans les favoris | Non | `FavoritesFragment.kt` | Termine |
| **BUS-13** | Bug | Elimination des doublons d'arrets dans la recherche | Non | `SearchFragment.kt` | Termine |
| **BUS-14** | Story | Filtre de recherche d'arrets par nom | Non | `StopListFragment.kt` | Termine |
| **BUS-15** | Story | Bouton de tri par Nom ou par Distance GPS | Non | `StopListFragment.kt` | Termine |
| **BUS-20** | Story | Deplacement de l'etoile favori (eviter chevauchement) | Non | `DetailsFragment.kt` | Termine |
| **BUS-21** | Story | Amelioration et placement des boutons d'aide | Non | `MainActivity.kt`, `TutorialOverlay.kt` | Termine |
| **BUS-23** | Story | Animation lors de l'utilisation du bouton retour | Non | `MainActivity.kt` | Termine |

### 15.2. Taille et structure

Le fichier fait 192 lignes.

Il est structure en 8 grandes sections :

1. Fiche signaletique et dependances
2. Modele de donnees et persistance
3. Couche reseau et API temps reel
4. Services d'arriere-plan
5. Composants UI et navigation
6. Widgets ecran d'accueil
7. Tutoriel et ergonomie visuelle
8. Tableau de bord Jira

### 15.3. Ce que `docs/task.md` confirme

Le document est tres coherent avec le code trouve :

- package `com.pau.busapp`
- architecture `Single-Activity`
- fragments multiples
- carte OpenStreetMap
- alertes
- widgets
- `SharedPreferences`
- GTFS offline
- API IDELIS en HTTPS manuel

### 15.4. Incoherences ou points a noter

Quelques ecarts / points d'attention ressortent :

- `docs/task.md` annonce 45 tickets Jira, mais le tableau enumererait 46 identifiants dans les sections visibles
- le tableau backlog contient une duplication semantique autour de l'indisponibilite API :
  - BUS-33
  - BUS-34
- le texte parle d'un stockage concentre dans `app_prefs`, mais le code utilise plusieurs preferences specialisees
- la spec parle d'une approche sans dependance lourde, mais `app/build.gradle` contient tout de meme OkHttp, meme si le code visible n'en depend pas directement pour l'API principale
- la spec signale une traduction incomplete, mais le depot contient deja de nombreux dossiers `values-*`

### 15.5. Tickets les plus notables

Parmi les tickets backlog :

- traduction incomplete
- gestion des deux directions dans les notifications
- message "Bus passe" dans la notification
- auto-scroll depuis notification
- regroupement des alertes
- notification conservee en cas de perte de connexion
- tri par prochain passage
- crash widget
- modernisation widget
- bug d'affichage / crash ponctuel
- batterie excessive
- vacances scolaires
- recherche sans accents
- API temporairement indisponible
- arrets FeBus mal categorises
- ajout toast a la creation d'alerte
- bandeau d'information reseau
- correction du calcul de retard / avance
- correction du trace de ligne sur la carte
- affichage des terminus sur la carte
- horloge lisible en theme sombre
- assistant Google
- widget qui consomme trop ou faux statut hors ligne
- analytics sur les onglets
- loader de recherche d'arrets proches

Parmi les tickets deja termines :

- page "A propos"
- conservation du scroll
- blocage des clics pendant le tutoriel
- correction des destinations dans la liste
- gestion du bouton retour
- bouton "Emuler ici" en option
- vue ligne depuis un arret
- redirection vers la ligne au clic sur le badge
- tutoriel contextuel
- suppression de la bulle vide sur la polyline
- suppression de l'epingle inactive
- suppression des doublons en recherche
- filtre de recherche par nom
- tri par nom ou distance
- etoile favori repositionnee
- boutons d'aide ameliores
- animation du bouton retour

## 16. Points forts

- architecture claire et modulaire
- tres bonne couverture fonctionnelle du domaine transport
- mode offline GTFS present
- widgets riches et personnalisables
- personnalisation poussee de l'interface
- prise en charge du multi-langue
- tutoriel integre
- gestion des etats API et reseau deja prevue

## 17. Points de vigilance

- duplication probable du projet dans `Android Studio/`
- absence de dossiers de tests observes
- plusieurs fichiers build / crash logs dans le depot
- cle API IDELIS presente dans la configuration Gradle
- nombreux tickets encore ouverts dans `docs/task.md`
- la couche widget est tres complexe et peut etre fragile
- la logique de passage en temps reel et de fallback GTFS semble subtile et merite des tests fonctionnels

## 18. Conclusion

Le projet Pau'delis est deja une application transport tres avancee, avec une base technique solide et une vision produit assez claire.

Le code couvre bien :

- la consultation du reseau
- la navigation sur carte
- les favoris
- les alertes
- les widgets
- la personnalisation

`docs/task.md` confirme cette direction et sert de vraie feuille de route produit / backlog. Le principal travail restant semble moins structurel que fonctionnel : corrections de bugs, harmonisation des notifications, fiabilisation du realtime, optimisation batterie, et finalisation de certaines experiences utilisateur.
