# 🚌 Spécification Technique & Guide d'Architecture Intégral : Pau'delis (BusV6)

---

## 1. 📌 Fiche Signalétique & Dépendances
* **Nom du package** : `com.pau.busapp`
* **Plateforme** : Android Native (Kotlin)
* **Architecture UI** : Single-Activity (`MainActivity`) + Multi-Fragments (AndroidX Navigation custom avec `FragmentTransaction` et gestion du BackStack via `OnBackPressedCallback`).
* **Bibliothèques Clés** :
  * **Cartographie** : `org.osmdroid:osmdroid-android` (OpenStreetMap).
  * **Concurrence** : Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`).
  * **Réseau** : Custom SSL Sockets en HTTP/1.1 (`javax.net.ssl.SSLSocketFactory`) pour communiquer directement avec l'API IDELIS sans dépendance lourde externe.
  * **Widgets** : `AppWidgetProvider`, `RemoteViews`, `RemoteViewsService`.
  * **Persistance** : `SharedPreferences` (`app_prefs`) avec sérialisation JSON pour les alertes, favoris et configurations.

---

## 2. 🗄️ Modèle de Données & Persistance

### A. Données Statiques du Réseau (`AppData.kt`)
`AppData.kt` agit comme une base de données statique en mémoire (~130 Ko) contenant :
* La liste complète des lignes de transport IDELIS (FéBus, T1 à T4, Lignes 5 à 16, Coxitis, etc.).
* La liste des arrêts physiques avec leurs identifiants (`stopCode`), noms, coordonnées GPS (`lat`, `lon`), et lignes desservies.
* Le tracé polyline GPS des itinéraires pour chaque ligne et direction.

### B. Modèle d'Alerte (`Alert.kt` & `AlertManager.kt`)
* **Data Class `Alert`** :
  ```kotlin
  data class Alert(
      val id: Long = System.currentTimeMillis(),
      val stopName: String,
      val lineName: String,
      val destination: String = "",
      val hourMinute: Pair<Int, Int>,
      val minutesBefore: Int,
      val conditions: Set<AlertCondition> = emptySet(),
      val isToday: Boolean = false,
      val weekdays: Set<Int> = emptySet(),
      val specificDates: List<String> = emptyList(),
      val excludedDates: List<String> = emptyList(),
      val enabled: Boolean = true
  )
  ```
* **Conditions d'activation (`AlertCondition`)** : `ODD_WEEKS`, `EVEN_WEEKS`, `NO_SCHOOL_HOLIDAYS`, `SPECIFIC_DATES`, `WEEKDAYS`.
* **Persistance** : Stockage au format JSON dans `SharedPreferences` via `AlertManager.kt`.

### C. Modèle des Favoris (`FavoritesManager.kt`)
* Gère l'enregistrement local des arrêts favoris et des lignes préférées sous forme de jeux d'identifiants JSON dans les `SharedPreferences` (`app_prefs`).

---

## 3. 🌐 Couche Réseau & API Temps Réel (`IdelisApi.kt`)

* **Endpoint HTTP/1.1** : `https://api.idelis.fr/GetStopMonitoring`
* **Authentification** : En-tête personnalisé `X-AUTH-TOKEN: BuildConfig.IDELIS_API_KEY`
* **Fonctionnement Sockets SSL** :
  1. Ouverture directe d'une connexion TLS sur le port 443 via `SSLSocketFactory.getDefault().createSocket("api.idelis.fr", 443)`.
  2. Envoi manuel de la requête HTTP `GET` avec payload JSON `{"code":"<stopCode>", "next": 5}`.
  3. Gestion automatique du décodage *Chunked Transfer Encoding* via la méthode `dechunk()`.
* **Objets de réponse** :
  * `PassageStatut` : Enum (`THEORIQUE`, `A_LHEURE`, `RETARD`, `AVANCE`, `ANNULE`).
  * `Passage` : Heure d'arrivée, type (`reel` ou `theorique`), statut, écart en minutes (`ecartMin`).
  * `StopInfo` : Nom de ligne, destination, accessibilité PMR, liste des `Passage`.

---

## 4. ⚙️ Services d'Arrière-Plan & Backgrounding

### A. Service de Suivi Continu (`TrackingService.kt`)
* **Type** : Service Android au premier plan (*Foreground Service* avec notification persistante).
* **Rôle** : Reçoit l'ordre de suivre le passage imminent d'un bus pour une alerte donnée.
* **Fonctionnement** :
  * Interroge l'API IDELIS en boucle toutes les $X$ secondes.
  * Met à jour la notification avec le temps restant réel.
  * Déclenche une alarme sonore/vibration à l'approche du bus ($N$ minutes avant).
  * Lors de la désactivation manuelle depuis le bouton de la notification, stoppe le service et annule la notification (`NotificationManager.cancel`).

### B. Receveurs d'Événements System (`AlertReceiver.kt` & `BootReceiver.kt`)
* **`AlertReceiver.kt`** : Réveille l'application via `AlarmManager` au moment défini pour lancer le `TrackingService`.
* **`BootReceiver.kt`** : Écoute `ACTION_BOOT_COMPLETED` pour reprogrammer toutes les alertes actives dans l'`AlarmManager` au démarrage du téléphone.

---

## 5. 📱 Composants UI & Navigation

### A. Activité Principale (`MainActivity.kt`)
* Gère la barre de navigation personnalisée avec rechargement dynamique des onglets via `rebuildNav()`.
* Intègre la gestion du bouton Retour (`onBackPressedDispatcher`) pour dépiler correctement le `supportFragmentManager`.
* Déclenche l'overlay du tutoriel contextuel (`showTutorial()`).

### B. Écran Carte (`MapFragment.kt` & `LineMapFragment.kt`)
* Utilise `osmdroid` pour le rendu cartographique OpenStreetMap.
* Affiche la position de l'usager, les arrêts environnants et la position calculée/réelle des bus.
* **Mode Simulation ("Émuler ici")** : Permet de placer un repère virtuel sur la carte pour simuler la position GPS. Bouton masquable via les paramètres.
* **`LineMapFragment.kt`** : Affiche le tracé `Polyline` d'une ligne spécifique sans popup vide superflue (*InfoWindow* masquée).

### C. Consultation d'un Arrêt (`DetailsFragment.kt` & `StopListFragment.kt`)
* **`StopListFragment.kt`** : Liste de tous les arrêts avec recherche rapide et tri (par nom ou distance GPS).
* **`DetailsFragment.kt`** :
  * Affiche les temps de passage par ligne/destination pour l'arrêt sélectionné.
  * Badges de lignes colorés dynamiquement.
  * Maintien exact de la position de défilement (*Scroll Position*) lors des rafraîchissements automatiques.
  * Mention « Pas d'infos » affichée clairement si aucun passage n'est disponible.
  * Redirection directe du bouton « Bus » vers la carte du tracé de la ligne (`LineMapFragment`).

### D. Recherche & Favoris (`SearchFragment.kt` & `FavoritesFragment.kt`)
* **`SearchFragment.kt`** : Filtre les arrêts uniques (regroupement des quais physiques sous un seul arrêt conceptuel).
* **`FavoritesFragment.kt`** : Liste des arrêts et lignes favoris. Icône d'épingle retirée pour un visuel épuré.

### E. Paramètres & Accessibilité (`SettingsFragment.kt`)
* **Régulation du thème** : Clair / Sombre / Système (`ThemeManager.kt`).
* **Daltonisme** : Correction chromatique via `ColorblindManager.kt`.
* **Langues (`LocaleHelper.kt`)** : Bascule dynamique de la locale sans redémarrage requis (`fr`, `en`, `es`).
* **Switch d'Émulation** : Contrôle la visibilité du bouton "Émuler ici" sur la carte.

---

## 6. 🧩 Widgets Écran d'Accueil (App Widgets)
L'application propose des widgets de bureau entièrement configurables :
* **`StopsWidgetProvider.kt`** : Implémentation principale d'`AppWidgetProvider`.
* **`WidgetConfigActivity.kt` & `WidgetStopConfigDialog.kt`** : Interfaces de configuration du widget.
* **`WidgetListService.kt`** : Service de fourniture de la `RemoteViewsFactory` pour le défilement des horaires.
* **`WidgetOrderManager.kt`, `WidgetLinesManager.kt`** : Stockage local de la disposition personnalisée.

---

## 7. ♿ Tutoriel & Ergonomie Visuelle (`TutorialOverlay.kt`)
* Overlay d'aide interactif dessiné par-dessus l'interface.
* Utilise `SpannableString` et `ForegroundColorSpan` pour colorer spécifiquement les mots de couleur (*Vert*, *Orange*, *Bleu*, *Rouge barré*).
* Intercept les évènements tactiles (`isClickable = true`, `isFocusable = true`) pour bloquer les clics arrière-plan.

---

## 8. 📊 Tableau de Bord Intégral des 45 Tickets Jira (`BUS`)

### 🟢 Backlog / Avancement (29 Tickets Jira - Ordre Croissant)

| Ticket | Type | Demande / Description Jira | CE (Attente Capture) | Composant Code Impacté | Statut | Plan d'Action Technique |
| :---: | :---: | :--- | :---: | :--- | :---: | :--- |
| **BUS-3** | 🐛 Bug | Traduction incomplète de l'application (EN / ES) | Non | `strings.xml` (`values-*`) | fait | Traduction intégrale de tous les composants de l'application et du tutoriel. |
| **BUS-16** | 📖 Story | Gestion des 2 directions pour un même arrêt dans les notifs | Non | `AddAlertDialog.kt`, `Alert.kt` | fait | Stocker la destination explicite dans le modèle `Alert` pour cibler la bonne direction. |
| **BUS-17** | 📖 Story | Écrire "Bus passé" au lieu de l'horaire périmé dans la notif | Non | `TrackingService.kt` | pas encore fait | Mettre à jour le texte de la notification avec `R.string.bus_passed` au franchissement de l'heure. |
| **BUS-18** | 📖 Story | Clic sur notification -> Défilement auto jusqu'à l'arrêt | Non | `MainActivity.kt`, `StopListFragment.kt` | pas encore fait | Transmettre l'ID de l'arrêt dans le `PendingIntent` de notification et scroller la liste. |
| **BUS-19** | 📖 Story | Ranger les alertes par nom d'arrêt (menus déroulants) | Non | `AlertsFragment.kt` | pas encore fait | Implémentation d'un RecyclerView à en-têtes extensibles (*Expandable Grouping*). |
| **BUS-22** | 🐛 Bug | Notification qui disparaît lors d'une perte de connexion | Non | `TrackingService.kt` | pas encore fait | Conserver la notification en indiquant "Connexion perdue..." sans appeler `cancel()`. |
| **BUS-24** | 📖 Story | Trier les arrêts par prochain passage | Non | `StopListFragment.kt` | pas encore fait | Ajouter un comparateur sur les minutes restantes du prochain bus. |
| **BUS-25** | 📖 Story | Clic sur notification -> Défilement auto jusqu'à la ligne | Non | `MainActivity.kt`, `DetailsFragment.kt` | pas encore fait | Transmettre l'ID de la ligne via l'Intent de la notif et scroller le RecyclerView. |
| **BUS-26** | 🐛 Bug | Crash au clic sur "Organiser le widget" | Non | `WidgetConfigActivity.kt`, `WidgetOrderManager.kt` | pas encore fait | Fix `NullPointerException` dans l'Intent ou la liste d'ordonnancement du widget. |
| **BUS-27** | 📖 Story | Moderniser le widget | Non | `StopsWidgetProvider.kt`, `res/layout/widget_*` | pas encore fait | Redesign des RemoteViews avec coins arrondis et style Material You. |
| **BUS-28** | 🐛 Bug | Bug général d'affichage / crash ponctuel | Non | Plusieurs fragments | pas encore fait | Diagnostic et correction selon les logs de crash. |
| **BUS-29** | 📖 Story | Rechargement auto des horaires au retour sur l'app | Non | `MainActivity.kt`, `DetailsFragment.kt` | pas encore fait | Déclencher le rafraîchissement dans la méthode `onResume()` des fragments actifs. |
| **BUS-30** | 🐛 Bug | Consommation batterie excessive (8% pour 5min d'écran) | Non | `TrackingService.kt`, `MapFragment.kt` | pas encore fait | Réduire la fréquence de rafraîchissement GPS et suspendre les coroutines inactives. |
| **BUS-31** | 📖 Story | Période vacances scolaires | Non | `Alert.kt`, `AlertManager.kt` | pas encore fait | Intégration du calendrier des vacances pour désactiver automatiquement les alertes `NO_SCHOOL_HOLIDAYS`. |
| **BUS-32** | 🐛 Bug | Obligé de mettre les accents dans la recherche | Non | `SearchFragment.kt`, `StopListFragment.kt` | pas encore fait | Normaliser les chaînes avec `java.text.Normalizer` pour ignorer les diacritiques. |
| **BUS-33** | 🐛 Bug | En ligne alors que API temporairement indisponible (doublon) | Non | `IdelisApi.kt` | pas encore fait | Fusionner avec le traitement du ticket BUS-34. |
| **BUS-34** | 🐛 Bug | En ligne alors que l'API est temporairement indisponible | Non | `IdelisApi.kt`, `DetailsFragment.kt` | pas encore fait | Gestion d'erreur HTTP 5xx/SocketTimeout avec bandeau "API indisponible" explicite. |
| **BUS-35** | 🐛 Bug | Arrêts du FéBus (F) en orange alors qu'il n'y passe pas | Non | `AppData.kt`, `MapFragment.kt` | pas encore fait | Correction du filtrage des lignes associées aux marqueurs d'arrêts du FéBus. |
| **BUS-36** | 📖 Story | Ajouter un toast quand alerte ajoutée | Non | `AddAlertDialog.kt`, `AlertsFragment.kt` | pas encore fait | Affichage d'un `Toast.makeText(context, R.string.alert_added, Toast.LENGTH_SHORT).show()`. |
| **BUS-37** | 📖 Story | Ajouter bandeau d'information en ligne CE | **Oui** | `LinesFragment.kt`, `LineDetailFragment.kt` | pas encore fait | Composant Marquee / Banner affichant les perturbations du réseau envoyées par l'API (En attente de capture d'écran). |
| **BUS-38** | 📖 Story | Dégradé en haut de l'arrêt avec les couleurs des bus y passant | Non | `DetailsFragment.kt` | pas encore fait | Génération dynamique d'un `GradientDrawable` basé sur les couleurs des badges de ligne. |
| **BUS-39** | 🐛 Bug | Marqué en avance alors qu'en retard de 7 min. CE | **Oui** | `PassageHelper.kt`, `IdelisApi.kt` | fait | Corriger le calcul de l'écart `ecartMin` et le mapping de l'enum `PassageStatut` (En attente de capture d'écran). |
| **BUS-40** | 🐛 Bug | Le bus ne passe pas sur la route CE | **Oui** | `LineMapFragment.kt`, `AppData.kt` | pas encore fait | Corriger les coordonnées GPS du tracé de ligne pour coller au réseau routier OSM (En attente de capture d'écran). |
| **BUS-41** | 📖 Story | Ajouter le nom des terminus sur la carte de l'itinéraire. CE | **Oui** | `LineMapFragment.kt` | pas encore fait | Ajouter des marqueurs textuels aux coordonnées de départ et d'arrivée du tracé `Polyline` (En attente de capture d'écran). |
| **BUS-42** | 🐛 Bug | Quand bus en retard, horloge en noir et donc illisible pour le thème sombre. Solution : la mettre en orange. CE | **Oui** | `PassageHelper.kt`, `DetailsFragment.kt` | fait | Passer la couleur de l'horloge/icône de retard en **orange** adaptatif au thème sombre (En attente de capture d'écran). |
| **BUS-43** | 📖 Story | Option de commander l'app avec l'Assistant Google ? | Non | `AndroidManifest.xml`, `actions.xml` | pas encore fait | Ajout des App Actions / Shortcuts Google Assistant pour les commandes vocales. |
| **BUS-44** | 🐛 Bug | Widget actif sans l'être (batterie) 🔋 + faux statut hors-ligne | Non | `StopsWidgetProvider.kt`, `WidgetListService.kt` | fait | Ajuster la fréquence d'actualisation du widget (`updatePeriodMillis`) et corriger le check réseau. |
| **BUS-45** | 📖 Story | Google Analytics sur les onglets | Non | `MainActivity.kt` | fait | Intégration de Firebase Analytics / Google Analytics pour tracer les changements d'onglets. |
| **BUS-46** | 📖 Story | Animation & texte "Recherche d'arrêts les plus proches en cours" au clic bouton | Non | `MapFragment.kt`, `StopListFragment.kt` | pas encore fait | Afficher un loader / dialog avec message de recherche active lors du clic sur la localisation. |

---

### 🔵 Tickets Terminés (17 Tickets Jira - Ordre Croissant)

| Ticket | Type | Titre / Fonctionnalité Réalisée | CE (Attente Capture) | Composant Implémenté | Statut |
| :---: | :---: | :--- | :---: | :--- | :---: |
| **BUS-1** | 📖 Story | Section "À propos" avec crédits développeur | Non | `MoreFragment.kt` | ✅ Terminé |
| **BUS-2** | 🐛 Bug | Conservation du défilement lors du rafraîchissement | Non | `DetailsFragment.kt` | ✅ Terminé |
| **BUS-4** | 🐛 Bug | Blocage des clics sur l'application pendant le tutoriel | Non | `TutorialOverlay.kt` (`isClickable = true`) | ✅ Terminé |
| **BUS-5** | 🐛 Bug | Correction des bugs de destination dans la liste des arrêts | Non | `StopListFragment.kt` | ✅ Terminé |
| **BUS-6** | 📖 Story | Prise en charge du bouton Retour (Back) physique du téléphone | Non | `MainActivity.kt` (`onBackPressedDispatcher`) | ✅ Terminé |
| **BUS-7** | 📖 Story | Masquer le bouton "Émuler ici" et l'ajouter en option paramètre | Non | `MapFragment.kt`, `SettingsFragment.kt` | ✅ Terminé |
| **BUS-8** | 📖 Story | Voir le tracé de la ligne sur la carte depuis l'arrêt | Non | `DetailsFragment.kt`, `LineMapFragment.kt` | ✅ Terminé |
| **BUS-9** | 📖 Story | Redirection vers la ligne au clic sur le badge | Non | `DetailsFragment.kt` | ✅ Terminé |
| **BUS-10** | 📖 Story | Tutoriel contextuel affiché par page | Non | `MainActivity.kt`, `TutorialOverlay.kt` | ✅ Terminé |
| **BUS-11** | 🐛 Bug | Désactivation de la bulle d'info vide sur le tracé polyline | Non | `LineMapFragment.kt` | ✅ Terminé |
| **BUS-12** | 🐛 Bug | Suppression de l'épingle inactive dans les favoris | Non | `FavoritesFragment.kt` | ✅ Terminé |
| **BUS-13** | 🐛 Bug | Élimination des doublons d'arrêts dans la recherche | Non | `SearchFragment.kt` | ✅ Terminé |
| **BUS-14** | 📖 Story | Filtre de recherche d'arrêts par nom | Non | `StopListFragment.kt` | ✅ Terminé |
| **BUS-15** | 📖 Story | Bouton de tri par Nom ou par Distance GPS | Non | `StopListFragment.kt` | ✅ Terminé |
| **BUS-20** | 📖 Story | Déplacement de l'étoile favori (éviter chevauchement) | Non | `DetailsFragment.kt` | ✅ Terminé |
| **BUS-21** | 📖 Story | Amélioration et placement des boutons d'aide | Non | `MainActivity.kt`, `TutorialOverlay.kt` | ✅ Terminé |
| **BUS-23** | 📖 Story | Animation lors de l'utilisation du bouton retour | Non | `MainActivity.kt` | ✅ Terminé |
