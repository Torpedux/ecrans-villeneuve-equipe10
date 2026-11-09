# DAT — sections 8 (suite) et 11 (brouillon)

> Complète la section 8 de `sections_1_2_8.md` (tableau des invariants déjà rédigé) avec les **value objects** et les **événements et policies**, et rédige le tableau de la section 11. Sources : code de `code/vente/src/main/java/com/ecrans/vente/domaine`, ADR-0001 et ADR-0002.

## 8. Modèle du domaine (suite)

### Value objects

Un value object n'a pas d'identité : deux valeurs égales sont interchangeables. Ils sont immuables (Java `record`) et refusent à la construction toute valeur invalide, donc un objet existant est toujours valide.

| Value object | Rôle | Règle qu'il garde | Où il vit |
|---|---|---|---|
| `PlaceId` | Un fauteuil du plan de salle : rang (A à Z) + numéro (« G7 ») | Rang entre A et Z, numéro ≥ 1 ; ordre naturel (rang puis numéro), utile pour poser les blocages dans un ordre stable | `domaine/commun` |
| `SeanceId` | Identifiant d'une séance, attribué par la Programmation (« S42 ») | Non vide. La Vente ne le fabrique jamais : elle le reçoit | `domaine/commun` |
| `CommandeId` | Identifiant d'une commande, aussi la `reference` transmise à PayVia | Non vide ; généré à la création (`C-` + 8 caractères) | `domaine/commun` |
| `Montant` | Une somme en euros, stockée en **centimes** | Jamais négatif ; pas d'erreur d'arrondi (long, pas de décimal). Sert au prix, au total (B6) et au remboursement | `domaine/commun` |
| `Categorie` | Plein, réduit, enfant, matin, abonné | Ensemble fermé : la Vente choisit la catégorie, la Tarification donne le prix | `domaine/commande` |
| `Tarif` | Une catégorie + le prix calculé **au moment du blocage** | **B6 : prix figé.** Le prix ne change plus après le choix, même si le marketing modifie la grille | `domaine/commande` |
| `Billet` | Une place + son tarif. Vit à l'intérieur de `Commande` | Place et tarif obligatoires. Le total d'une commande est la somme des prix de ses billets | `domaine/commande` |
| `EtatPlace` | L'état d'un fauteuil pour une séance : statut (libre, bloquée, vendue), commande, fin de blocage | **B2 :** un blocage échu ne retient plus la place (`estLibreA`), même si le planificateur ne l'a pas encore nettoyée. Fabriques `libre()`, `bloquee()`, `vendue()` | `domaine/seance` |
| `StatutPlace`, `StatutCommande` | Les états possibles d'une place (`LIBRE`, `BLOQUEE`, `VENDUE`) et d'une commande (`EN_ATTENTE_PAIEMENT`, `PAYEE`, `EXPIREE`, `A_REMBOURSER`) | Les transitions interdites sont refusées par `Commande` (`TransitionInterdite`) | `domaine/seance`, `domaine/commande` |

Les identifiants `SeanceId` et `CommandeId` sont aussi la seule façon dont `Seance` et `Commande` se référencent (ADR-0002) : aucun des deux agrégats ne tient l'autre en mémoire.

### Événements et policies

Un événement dit ce qui s'est passé, **au passé**. Il est créé par l'agrégat (`Commande` les range dans une liste interne), puis publié par le service d'application **après** l'enregistrement, via le port `PublicateurEvenements`. Si l'enregistrement échoue, rien n'est publié. Les quatre événements sont les cas de l'interface scellée `EvenementVente`. Tous portent la commande concernée et l'instant `survenuLe`.

| Événement | Émis par | Quand | Contenu | Réactions (policies) |
|---|---|---|---|---|
| `CommandePayee` | `Commande.payer` (via `ConfirmerPaiementService`) | Le paiement est confirmé et les places sont passées à « vendue » | Commande, séance, places, total | **Envoyer les billets** (Notification : QR code par email/SMS) · **Ajouter les billets à la liste des billets valides** (Contrôle d'accès) · **Compter l'entrée et le prix payé** (Déclaration CNC) |
| `CommandeExpiree` | `Commande.expirer` (via `ExpirerBlocagesService`, planificateur chaque minute) | Le blocage de 10 min est échu sans paiement (B2) | Commande, séance | Aucune réaction nécessaire pour la Vente : les places sont déjà libérées dans la même opération. Utile aux statistiques. |
| `PaiementARembourser` | `Commande.aRembourser` (via `ConfirmerPaiementService`) | Paiement reçu trop tard et places reprises par un autre client (B13) | Commande, montant | **Rembourser le client via PayVia** · **Envoyer l'avis de remboursement** (Notification) |
| `VentePartenaireEnregistree` | `EnregistrerVentePartenaireService` | Une vente déjà payée est arrivée d'un partenaire par son ACL (B11) | Commande, partenaire, référence partenaire, montant payé | **Déclarer l'entrée au CNC avec le prix payé par le spectateur** · **Calculer le reversement dû au partenaire (tarif − 8 %)** (Finance) |

**Policies** (règles « quand X arrive, alors faire Y ») :

| Policy | Déclencheur | Action | État |
|---|---|---|---|
| Libération automatique des blocages (B2) | Chaque minute, `ExpirationPlanifiee` | `ExpirerBlocagesService` libère les blocages échus et fait expirer les commandes concernées | Implémentée, testée |
| Paiement tardif (B13) | Notification de paiement reçue après l'expiration | Vente si les places sont encore libres, sinon `Commande.aRembourser` | Implémentée (`ConfirmerPaiementService`), testée (`bonus/PaiementTardifTest`) |
| Idempotence du paiement (B14) | Notification en double de PayVia | Ignorée si la commande est déjà `PAYEE` ou `A_REMBOURSER` | Implémentée, testée (`bonus/PaiementEnDoubleTest`) |
| Remboursement automatique | `PaiementARembourser` | Demander le remboursement à PayVia | **À faire** : l'événement est émis, mais aucun port ni adapter de remboursement n'existe encore |
| Envoi des billets | `CommandePayee` | Appel au service d'envoi | **À faire** : aucun consommateur branché |
| Billets valides du Contrôle, déclaration CNC | `CommandePayee`, `VentePartenaireEnregistree` | Alimenter les contextes aval | **À faire** : contextes hors du périmètre codé |

Aujourd'hui le publicateur est `PublicateurEnMemoire` : il conserve les événements pour les tests, sans les livrer à personne. La livraison réelle reste à brancher (voir section 11).

## 11. Risques et dette technique

| Risque ou simplification | Impact | Parade |
|---|---|---|
| **Persistance en mémoire** : `SeancesEnMemoire` et `CommandesEnMemoire` simulent la base. Le verrou optimiste est testé, mais pas sur PostgreSQL (C1) | Une panne ou un redémarrage perd tout ; rien ne prouve encore B1 sous vraie concurrence en base | Adapter JPA avec `@Version` (`UPDATE … WHERE version = ?`) et rejouer `ConcurrenceTest` sur PostgreSQL ; test de charge du pic (420 places en 5 min, p95 < 1 s) |
| **Faux conflits au pic** (ADR-0002) : le verrou porte sur toute la séance | Au pic d'avant-première, des clients qui veulent des places différentes se gênent ; après 3 essais, un 409 alors que la place était libre | Mesurer le taux de 409 ; augmenter les essais avec une attente aléatoire ; en dernier recours, verrou par place (`UNIQUE(seance, place)`) pour les séances au pic. Le port `Seances` isole ce changement |
| **Trou dans B13** (ADR-0002) : blocage expiré, place reprise par un autre client avant le planificateur, puis paiement de la commande d'origine | La commande reste `EN_ATTENTE_PAIEMENT`, `aRembourser` refuse la transition : un client a payé sans billet ni remboursement | Autoriser `aRembourser` depuis `EN_ATTENTE_PAIEMENT`, ou faire expirer la commande quand sa place est reprise ; ajouter le test bonus correspondant |
| **Événements non livrés** : `PublicateurEnMemoire` ne les transmet à personne | Billets non envoyés, remboursements non faits, CNC et Contrôle non alimentés, même si la vente est conclue | Brancher de vrais consommateurs ; pour ne pas perdre un événement entre l'enregistrement et la publication, passer à une table « outbox » écrite dans la transaction |
| **Paiement simulé** : `PaiementsSimules` et `WebhookPayVia` sont testés sur le format de l'annexe A, sans appel réel à PayVia | Écarts possibles à l'intégration (signature, délais, réessais 1 min / 5 min / 30 min / 2 h) | Test de contrat avec le bac à sable PayVia avant l'ouverture ; PayVia est un contrat de 4 ans (C3) |
| **Entorse « un agrégat par transaction »** (ADR-0002) : réserver et confirmer écrivent `Seance` et `Commande` ensemble | Les deux agrégats sont couplés ; la transaction s'allonge, et le jour où une partie sort de la base unique, la garantie « payée ⇒ vendue » est à repenser | Assumée : la cohérence immédiate prime. À réexaminer si la Vente est un jour découpée |
| **Planificateur et expiration** : `ExpirerBlocagesService` passe chaque minute et peut perdre contre une vente en cours | Une place peut rester bloquée environ 1 minute de plus que prévu (B2) | Le blocage échu ne retient déjà plus la place à la lecture (`EtatPlace.estLibreA`) : le retard n'empêche pas de la revendre |
| **Tarification pas encore séparée** : `GrilleTarifaireFixe` remplace le contexte Tarification | Prix en dur ; une opération (Fête du cinéma) ou un nouveau supplément demande une livraison | Remplacer l'adapter par un client de la Tarification (le cœur ne bouge pas) ; garder une copie locale de la grille, au cas où la Tarification ne répond pas au pic (ADR-0001) |
| **Abonnements en couches simples** (ADR-0001) | Avec 1 600 abonnés et +30 % par an, les règles B7, B8, B16 risquent de se disperser ; une refonte vers un vrai modèle devient probable | Isoler les Abonnements derrière une interface (« cet abonné a-t-il droit à une place gratuite ? ») pour pouvoir les refondre sans toucher à la Vente |
| **Seconde ACL partenaire** (CinéMalin) non faite | Le délai « nouveau partenaire en moins de 2 semaines » n'est vérifié qu'avec SortiesFlash | Écrire l'ACL CinéMalin quand son format arrive ; seule cette ACL change, comme pour SortiesFlash à l'étape 8 |
| **Hypothèses non validées** (section 2) : remboursement des billets partenaires, résiliation d'un abonné, vente au guichet, mode hors ligne | Une règle supposée à tort donne un comportement faux en production | Valider chacune avec le référent concerné avant la mise en service (le double scan hors ligne est déjà accepté par K. Benali) |
| **Contraintes C1 à C6 non rédigées** (section 2) | Contraintes d'exploitation et de budget non traduites en exigences vérifiables | Compléter avec l'équipe |
| **Équipe de deux développeurs** et durée de vie de 10 ans | Dépendance à deux personnes ; le logiciel de 2011 s'arrête fin 2027, la migration est datée | Garder le cœur testé sans framework (domaine en Java pur), documenter les décisions en ADR, éviter les dépendances qui vieillissent mal |
