# Les Écrans de Villeneuve — Billetterie (équipe 10)

Projet final du module **Concevoir une application numérique** (Bachelor 3 Informatique, Ynov).
Nous concevons et construisons le cœur de la billetterie d'un multiplexe de 8 salles : la **Vente de places**, en architecture hexagonale et DDD, avec son Document d'Architecture Technique (DAT).

**Dépôt de l'équipe** : https://github.com/Torpedux/ecrans-villeneuve-equipe10

## Sommaire

- [Les Écrans de Villeneuve — Billetterie (équipe 10)](#les-écrans-de-villeneuve--billetterie-équipe-10)
  - [Sommaire](#sommaire)
  - [Le problème](#le-problème)
    - [Nos trois objectifs de qualité](#nos-trois-objectifs-de-qualité)
  - [L'équipe et la répartition des tâches](#léquipe-et-la-répartition-des-tâches)
  - [Architecture en bref](#architecture-en-bref)
    - [Un seul core : la Vente de places](#un-seul-core--la-vente-de-places)
    - [L'hexagone de la Vente](#lhexagone-de-la-vente)
    - [Parcours « réserver et payer »](#parcours--réserver-et-payer-)
    - [La décision défendue : ADR-0002 (verrou sur la séance)](#la-décision-défendue--adr-0002-verrou-sur-la-séance)
    - [Une hypothèse : pas de blocage au guichet](#une-hypothèse--pas-de-blocage-au-guichet)
  - [Organisation du dépôt](#organisation-du-dépôt)
  - [Lancer le projet](#lancer-le-projet)
    - [Les tests](#les-tests)
    - [L'application et Swagger](#lapplication-et-swagger)
  - [Les étapes du TP](#les-étapes-du-tp)
  - [Le DAT et les diagrammes](#le-dat-et-les-diagrammes)
  - [Restitution orale](#restitution-orale)
  - [Jalons](#jalons)
  - [Conventions de travail](#conventions-de-travail)

## Le problème

En mars, sur une avant-première au Grand Large (420 fauteuils), **14 places ont été vendues deux fois** : la salle s'est remplie en moins de 5 minutes. La règle **B1** est donc la priorité : *une place ne se vend qu'une fois, tous canaux confondus*.

Cinq canaux écrivent sur le même plan de salle : site web, appli, 4 bornes, 3 guichets et partenaires (SortiesFlash, CinéMalin). S'y ajoutent les agents d'accueil (douchettes) et le back-office (programmation, marketing, finance). Le logiciel de caisse de 2011 s'arrête fin 2027 : la billetterie doit durer au moins dix ans avec deux développeurs.

### Nos trois objectifs de qualité

| Priorité | Objectif | Mesure |
|---|---|---|
| 1 | **Cohérence de la vente** | 0 doublon sur 420 réservations concurrentes en 5 min |
| 2 | **Évolutivité** | Nouveau partenaire en moins de 2 semaines, nouvelle formule d'abonnement en moins d'un mois |
| 3 | **Tenue en charge** | Réservation répondue en moins de 1 s (p95) pendant le pic de 420 places en 5 min |

## L'équipe et la répartition des tâches

| Membre | Code Java (cœur et Spring) | DAT et diagrammes | Restitution orale |
|---|---|---|---|
| **Armand LEVINE** — Lead Domaine et Réservation | Étape 1 : `Seance.java`<br>Étape 3 : `ReserverDesPlacesService.java`<br>Étape 7.3 : `ReservationController` + DTOs | Diagramme de séquence (`4-sequence-reserver-payer`)<br>DAT sections 1, 2 (objectifs, hypothèses), 7 (vue dynamique) et 8 (modèle et invariants) | Le problème métier, les règles de vente et le déroulé de la séquence de réservation |
| **Matteo PETRONELLI** — Lead Commande, cycle de vie et PayVia | Étape 2 : `Commande.java`<br>Étape 4 : `ConfirmerPaiementService.java`<br>Étape 5 : `ExpirerBlocagesService.java`<br>Étapes 7.4 et 7.5 : webhook PayVia et `ExpirationPlanifiee` | Diagramme de contexte (`1-contexte`)<br>DAT sections 3 (contexte) et 9 (ADR-0002 : concurrence et agrégats) | La défense de l'ADR-0002 (verrou séance ou place, gestion du pic) et le cycle de vie d'une commande |
| **Aurel** — Lead Architecture, Spring et Partenaires | Étape 6 : correction de la dépendance cachée<br>Étapes 7.1, 7.2 et 7.6 : socle Spring, `VenteConfiguration`, `GestionErreurs`<br>Étape 8 : vente partenaire et `AclSortiesFlash` | Diagrammes de composants (`3-composants-vente`) et Context Map (`2-context-map`)<br>DAT sections 4, 5 (grille DDD), 6 (composants), 9 (ADR-0001) et 10 (tests et qualité) | La Context Map, l'architecture hexagonale et l'intégration partenaire (ACL) |

Le diagramme `5-modele-vente` illustre la section 8 du DAT (Armand).

## Architecture en bref

### Un seul core : la Vente de places

Nous avons classé sept contextes avec la grille de décision (DAT §5). Seule la Vente dépasse le seuil (total ≥ 14 **et** au moins 2 experts accessibles) : elle reçoit l'architecture **hexagonale + DDD**.

| Contexte | Total | Experts | Architecture retenue |
|---|---|---|---|
| **Vente** | 18 | 3 | **Hexagonal + DDD** |
| Abonnements | 15 | 1 | Couches |
| Tarification | 12 | 1 | Couches |
| Contrôle d'accès | 11 | 2 | Couches |
| Déclaration CNC | 11 | 2 | Couches |
| Programmation | 9 | 1 | CRUD + ACL |
| Notification | 8 | 1 | Solution du marché |

**Simplification assumée : les Abonnements.** Le total est de 15, mais l'experte (S. Okafor) n'est disponible qu'une fois par mois, et les formules changent trop souvent pour être codées. Nous gardons des couches simples avec des formules paramétrées (ADR-0001). Avec +30 % d'abonnés par an, une refonte vers un modèle DDD est probable.

### L'hexagone de la Vente

Toutes les dépendances vont vers le centre : le domaine ne connaît que Java.

- **Domaine** : deux agrégats, `Seance` (le plan de salle) et `Commande` (l'achat), qui se référencent par identité (`SeanceId`, `CommandeId`) ; value objects (`PlaceId`, `Montant`, `Tarif`, `EtatPlace`…) ; événements (`CommandePayee`, `CommandeExpiree`, `PaiementARembourser`, `VentePartenaireEnregistree`).
- **Application** : cas d'usage (`ReserverDesPlaces`, `ConfirmerPaiement`, `ExpirerBlocages`, `EnregistrerVentePartenaire`), ports entrants et sortants.
- **Adapters primaires** : API REST (`ReservationController`), webhook PayVia, planificateur `ExpirationPlanifiee`, ACL `AclSortiesFlash`.
- **Deux ACL** : `WebhookPayVia` et `AclSortiesFlash`. Leur vocabulaire reste dans leur package. Les étapes 7 et 8 se sont branchées sans toucher au domaine.

### Parcours « réserver et payer »

1. **Réserver** : `Seance.bloquer`, tout ou rien (B1, B2, B4). Place déjà prise : 409.
2. **Payer** : le webhook PayVia appelle `ConfirmerPaiement`, qui fait `vendre` + `payer` et émet `CommandePayee`.
3. **Sans paiement** : chaque minute, le planificateur libère les blocages (B2) et émet `CommandeExpiree`.

### La décision défendue : ADR-0002 (verrou sur la séance)

Comment garantir B1 quand environ 200 commandes arrivent sur la même séance, surtout dans la première minute ?

| | Option A (choisie) | Option B (écartée) |
|---|---|---|
| Principe | Verrou optimiste sur la séance : `version` incrémentée à chaque enregistrement, `ConflitDeVersion` si périmée, on recharge et on réessaie (3 essais) | Verrou par place : contrainte `UNIQUE(seance, place)` en base |
| Pour | B1 garanti dans le domaine, testé sans base ; le *tout ou rien* est gratuit (une écriture) | Seuls deux clients qui veulent **le même fauteuil** entrent en conflit |
| Contre | Faux conflits au pic ; les ventes d'une séance passent en file | B1 descend dans la base, hors du domaine ; plusieurs verrous ordonnés (interblocages) |

Conflits estimés : 10 % au premier essai, 1 % au deuxième, 0,1 % au troisième. Parade : test de charge (p95 < 1 s) ; si les conflits sont trop nombreux, passage à l'option B pour les seules séances au pic, isolé derrière le port `Seances`.

### Une hypothèse : pas de blocage au guichet

Au guichet, en espèces, les places ne sont **pas** bloquées 10 minutes : le client est présent et la vente est conclue à l'encaissement. Le blocage (B2) sert à attendre un paiement distant. Autres hypothèses (DAT §2) : le partenaire rembourse son client (B11), la résiliation d'un abonné annule les places gratuites au-delà de la fin du mois (B7), les douchettes tiennent une journée hors ligne (B12).

## Organisation du dépôt

```
ecrans-villeneuve-equipe10/
├── README.md                              ← ce fichier
└── ecrans-villeneuve/
    ├── README.md                          ← modèle fourni (équipe, jalons)
    ├── code/
    │   └── vente/                         ← le cœur de la Vente, en Java 21 + Spring Boot 3.5
    │       ├── pom.xml
    │       ├── README.md                  ← détail des étapes du TP
    │       ├── fournis/                   ← tests des étapes 7 et 8
    │       └── src/
    │           ├── main/java/com/ecrans/vente/
    │           │   ├── domaine/           ← agrégats, value objects, événements
    │           │   ├── application/       ← ports (in/out) et services
    │           │   ├── adapter/           ← in (web, payvia, planification, sortiesflash) et out
    │           │   └── config/            ← composition root et configuration Spring
    │           └── test/java/com/ecrans/vente/   ← un dossier par étape, bonus, scénario
    └── docs/
        ├── DAT-Ecrans-de-Villeneuve.docx  ← le DAT (11 sections)
        ├── redaction/                     ← brouillons Markdown des sections du DAT
        └── diagrammes/                    ← sources (.drawio, .puml) et exports PNG
```

## Lancer le projet

**Prérequis** : JDK 21 et Maven (ou le Maven intégré d'IntelliJ).

Toutes les commandes se lancent depuis `ecrans-villeneuve/code/vente`.

### Les tests

```bash
mvn test
```

Dans IntelliJ : **Maven → vente → Lifecycle → test**, ou clic droit sur une classe de test → **Run**. Les tests de `bonus/` sont désactivés (`@Disabled`) tant qu'on ne retire pas l'annotation.

### L'application et Swagger

```bash
mvn spring-boot:run
```

Puis ouvrir http://127.0.0.1:8080/swagger-ui.html. Si le port 8080 est occupé, ajouter `server.port=8081` dans `src/main/resources/application.properties`.

Pour essayer le parcours dans Swagger :

1. Réserver `A1` et `A2` sur la séance `S42` : 201, avec un numéro de commande.
2. Réserver de nouveau `A1` : 409 (place déjà prise).
3. Envoyer la notification PayVia avec le numéro de commande en `reference` : la commande passe à payée.
4. Vente partenaire : `POST /partner/v2/orders` (SortiesFlash).

## Les étapes du TP

| Étape | Contenu | Tests | Règles |
|---|---|---|---|
| 1 | `Seance` : `bloquer`, `vendre`, `libererBlocagesExpires` | `etape1/SeanceTest` | B1, B2, B4 |
| 2 | `Commande` : `creer`, `total`, `payer`, `expirer` | `etape2/CommandeTest` | B3, B6 |
| 3 | `ReserverDesPlacesService` | `etape3/ReserverDesPlacesTest` | |
| 4 | `ConfirmerPaiementService` | `etape4/ConfirmerPaiementTest` | |
| 5 | `ExpirerBlocagesService` | `etape5/ExpirerBlocagesTest` | B2 |
| 6 | Correction de la dépendance dans le mauvais sens cachée dans le code fourni | tous, toujours verts | |
| 7 | Spring Boot : pom, REST, webhook PayVia, planificateur, gestion des erreurs | `etape7/ApiVenteTest` | |
| 8 | Vente partenaire SortiesFlash : cas d'usage + ACL | `etape8/VentePartenaireApiTest` | B11 |
| Bonus | Paiement en double (B14), paiement tardif (B13), concurrence | `bonus/` | B13, B14 |
| Scénario | Parcours complet d'une soirée | `ScenarioSoireeDuJeudiTest` | |

## Le DAT et les diagrammes

Le DAT (`ecrans-villeneuve/docs/DAT-Ecrans-de-Villeneuve.docx`) compte 11 sections : introduction, contraintes et hypothèses, contexte, domaine, choix d'architecture, vue composants, vue dynamique, modèle du domaine, décisions (ADR), qualité et tests, risques et dette technique.

| Diagramme | Section | Question |
|---|---|---|
| `1-contexte` | 3 | Avec qui la billetterie échange-t-elle ? |
| `2-context-map` | 4 | Comment les contextes collaborent-ils ? |
| `3-composants-vente` | 6 | Comment la Vente est-elle branchée sur le monde extérieur ? |
| `4-sequence-reserver-payer` | 7 | Comment des places sont-elles réservées puis payées, ou libérées ? |
| `5-modele-vente` | 8 | Quels agrégats protègent les règles de la vente ? |

Chaque diagramme existe en deux fichiers dans `ecrans-villeneuve/docs/diagrammes/` : sa source et son export PNG.

## Restitution orale

Le fil conducteur part du problème métier et finit par nos choix.

| Moment | Contenu | Qui |
|---|---|---|
| Le problème | Mars, les 5 canaux, les 3 objectifs de qualité | Armand |
| Le domaine | Grille de décision et Context Map, Abonnements volontairement simple | Aurel |
| L'architecture du core | Composants (Aurel), puis séquence réserver / payer / expirer (Armand puis Matteo) | Aurel, Armand, Matteo |
| Une décision | ADR-0002 : verrouiller la séance | Matteo |
| Une hypothèse | Pas de blocage au guichet | Armand |

Chacun répond aux questions sur toutes les parties, y compris celles qu'il n'a pas présentées.

## Jalons

- [x] **J4, phase 1** : glossaire, context map et grille dans le DAT
- [x] **J4, phase 2** : étapes 1 à 5 faites, recherches de l'étape 6
- [x] **Avant le J5** : étapes 7 et 8 faites (Spring Boot, vente partenaire, ACL SortiesFlash)
- [x] **J5, finalisation** : DAT complet (sections 5, 7, 11 à vérifier), export PDF dans `ecrans-villeneuve/docs/`
- [x] **Tag `v1.0`** poussé
- [x] **Rendu sur Moodle** : lien de ce dépôt, avant les passages

## Conventions de travail

- Les noms du code (`Seance`, `Commande`, `ReserverDesPlaces`…) sont ceux du glossaire et des diagrammes.
- Le DAT est un fichier Word : **une seule personne le modifie à la fois**. Mettre à jour avant, commiter et pousser juste après.
- Un diagramme = une personne à la fois.
- Chaque membre commite son propre travail.
