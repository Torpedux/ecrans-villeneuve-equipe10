# DAT — sections 4, 5, 6, 9 (ADR-0001) et 10 (brouillon Aurel)

> Les décisions 1 et 2 (ADR-0001) et la liste des bounded contexts sont des **propositions à valider avec l'équipe** : elles engagent toute l'architecture.

## 4. Domaine

### Glossaire

Les termes ambigus du dossier d'abord : un même mot n'a pas le même sens d'un contexte à l'autre.

| Terme | Contexte | Définition |
|---|---|---|
| **Séance** | Programmation | Un film, une salle et un horaire, publiés par le programmateur (J. Morel). Peut être annulée ou déplacée (B10). |
| **Séance** | Vente | Un **plan de salle** à vendre : chaque fauteuil avec son état (libre, bloqué, vendu), une heure de début et une version. Dans le code : l'agrégat `Seance`. |
| **Place** | Vente | Un fauteuil numéroté du plan de salle, identifié par son rang et son numéro (« G7 ») : `PlaceId`. Ne pas confondre avec une place *vendue*, qui est un billet. |
| **Blocage** | Vente | Mise de côté des places choisies pendant 10 minutes, le temps du paiement (B2). Une place bloquée n'est pas vendue ; sans paiement, elle redevient libre. |
| **Réservation** | Vente | Le geste du client qui choisit ses places : il crée une commande et bloque les places (`ReserverDesPlaces`). Pour un abonné, « réserver » veut dire prendre sa place gratuite (B7). |
| **Vente** | Vente | Passage définitif d'une place à l'état vendu, après paiement (`Seance.vendre`) ou à l'arrivée d'une vente partenaire déjà payée (B11). |
| **Commande** | Vente | L'achat d'un client : de 1 à 10 billets d'une même séance (B3), avec un statut (en attente de paiement, payée, expirée, à rembourser). Agrégat `Commande`. |
| **Billet** | Vente | Une place d'une commande et son tarif figé au moment du choix (B6). Après paiement, il est envoyé (QR code) et scanné au Contrôle. |
| **Plein** | Vente | Deux sens à ne pas mélanger : une **séance pleine** (plus aucun fauteuil libre sur le plan de salle, N. Ferrand) et le **tarif plein** (catégorie à 11,50 €). |
| **Catégorie de tarif** | Vente, Tarification | Plein, réduit, enfant, matin, abonné (`Categorie`). La Vente choisit la catégorie ; la Tarification en donne le prix. |
| **Tarif / prix** | Tarification | Le prix d'une catégorie pour une séance : grille + suppléments (3D, Grand Large), ou prix d'une opération en cours. |
| **Opération** | Tarification | Prix spécial sur une période (Fête du cinéma à 5 €) qui remplace la grille. |
| **Expiration** | Vente | Fin d'un blocage non payé : les places sont libérées et la commande passe à « expirée » (B2, `ExpirerBlocages`). |
| **Paiement tardif** | Vente | Paiement reçu après l'expiration (le lien PayVia vit 15 min, le blocage 10). Vente conclue si les places sont encore libres, sinon remboursement immédiat (B13). |
| **Abonné** | Abonnements | Client titulaire d'une formule (Villeneuve Illimité) : une place gratuite par séance, deux séances à venir au maximum (B7). |
| **No-show** | Abonnements | Billet d'abonné non scanné à la fin de sa fenêtre de validité. 3 en 30 jours suspendent la réservation à l'avance 7 jours (B8). |
| **Partenaire** | Vente | Plateforme (SortiesFlash, CinéMalin) qui vend nos places à ses clients et nous envoie la vente déjà payée, dans son format (B11). |
| **Reversement** | Vente, Finance | Ce que le partenaire nous verse chaque mois : le tarif de notre grille moins 8 % (B11). Différent du **montant payé** par le spectateur chez le partenaire, gardé pour le CNC. |
| **Annulation** | Vente / Programmation | Deux sens : le **client** annule sa commande (jusqu'à 2 h avant, B9) ; le **cinéma** annule la séance (remboursement automatique de tous, B10). |

### Sous-domaines

| Sous-domaine | Type | Stratégie |
|---|---|---|
| Vente de places | **Core** | Développé en interne, hexagonal + DDD. C'est là que se joue B1 (l'incident de mars : 14 places vendues deux fois) et le pic de 420 places en 5 min. |
| Tarification | Supporting | Développé en interne, simple : une grille et des opérations paramétrées par le marketing. |
| Abonnements | Supporting | Développé en interne, en couches, avec des formules paramétrées (voir ADR-0001). Stratégique pour la direction, mais sa valeur tient à l'offre commerciale, pas à un algorithme. |
| Programmation | Supporting | Développé en interne, CRUD : le programmateur « programme de la même façon depuis vingt ans ». |
| Contrôle d'accès | Supporting | Développé en interne, simple, avec un mode hors ligne (B12). |
| Déclaration CNC | Generic | Format imposé, identique pour tous les cinémas : on s'y conforme, et un module du marché serait envisageable. |
| Paiement | Generic | Acheté : PayVia (contrat de 4 ans, C3). |
| Notification (emails, SMS) | Generic | Délégué au service d'envoi du marché. |

**Pourquoi la Vente est le core** : c'est la seule partie où une erreur coûte directement la confiance des clients (B1), où les règles se croisent (blocage, expiration, paiement tardif, partenaires) et où se trouve le pic de charge. C'est aussi la seule dont l'experte (N. Ferrand) est disponible chaque semaine.

### Context map

Diagramme : `docs/diagrammes/2-context-map` — « Comment les contextes collaborent-ils ? »

| Amont (U) | Aval (D) | Pattern | Pourquoi |
|---|---|---|---|
| Programmation | Vente | Customer/Supplier, par événements (`SeancePubliee`, `SeanceAnnulee`) | La vente ouvre à la publication (B4). La Vente traduit la séance de la Programmation (film, salle, horaire) en **sa** séance (plan de salle). |
| Tarification | Vente | Customer/Supplier | La Vente demande le prix au moment du choix (B6) via son port `GrilleTarifaire` ; elle exprime ses besoins, la Tarification s'y engage. |
| Abonnements | Vente | Customer/Supplier | La Vente demande si l'abonné a droit à une place gratuite (B7). |
| PayVia | Vente | **ACL** (`WebhookPayVia`) | PayVia impose son format et ne le changera pas pour nous : on le traduit à l'entrée pour ne pas polluer le modèle de la Vente. |
| SortiesFlash | Vente | **ACL** (`AclSortiesFlash`) | Format imposé (annexe B) : `show_ref`, `ADULT`… restent dans `adapter/in/sortiesflash/`. |
| CinéMalin | Vente | **ACL** (à venir) | Même situation, autre format : une seconde ACL, rien d'autre ne change. |
| Vente | Contrôle d'accès | Published Language (événement `CommandePayee`) | Le Contrôle construit sa liste de billets valides à partir des ventes. |
| Vente | Déclaration CNC | Published Language (`CommandePayee`, `VentePartenaireEnregistree`) | Le CNC veut le prix payé par le spectateur, y compris chez les partenaires : il est dans l'événement. |
| Vente | Notification | Published Language (`CommandePayee`, `PaiementARembourser`) | Envoi des billets et des avis de remboursement. |
| Contrôle d'accès | Abonnements | Published Language (événement « billet scanné ») | Le scan prouve la venue de l'abonné : base du décompte des no-shows (B8). |
| FilmoData | Programmation | **ACL** | API en anglais, durées en secondes : traduites à l'entrée. |
| Portail CNC | Déclaration CNC | **Conformist** | 40 pages de spécification, « le CNC ne négociera pas » : on adopte son modèle tel quel. |
| Service d'envoi | Notification | Conformist | Prestataire du marché, on suit son API. |

## 5. Choix d'architecture par contexte

### Grille de décision

Notes de 1 (faible) à 3 (fort). « Experts » : 3 = expert disponible chaque semaine, 1 = rarement disponible.
Règle de lecture : **total ≥ 14 et experts ≥ 2 → hexagonal + DDD** ; total entre 10 et 13, ou experts trop peu disponibles → architecture en couches simple ; total ≤ 9 → CRUD ou solution du marché.

| Contexte | Complexité | Valeur | Durée de vie | Volatilité | Experts | Canaux | Total | Architecture retenue | Justification |
|---|---|---|---|---|---|---|---|---|---|
| **Vente** | 3 | 3 | 3 | 3 | 3 | 3 | **18** | **Hexagonal + DDD** | B1 à B4, B11, B13, B14 ; 5 canaux ; règles changées « plusieurs fois par an » ; N. Ferrand disponible tous les mardis. |
| Abonnements | 3 | 3 | 3 | 3 | 1 | 2 | 15 | Couches, formules paramétrées | Total élevé, mais S. Okafor n'est disponible qu'une fois par mois : un modèle DDD construit sans expert collerait mal au métier (ADR-0001). |
| Tarification | 2 | 2 | 3 | 3 | 1 | 1 | 12 | Couches, grille paramétrée | Change souvent, mais c'est du paramétrage (grille, suppléments, opérations), saisi en back-office. |
| Contrôle d'accès | 2 | 2 | 3 | 1 | 2 | 1 | 11 | Couches + mode hors ligne | Une seule règle forte (B12, sans réseau) ; un double scan en panne est accepté. |
| Déclaration CNC | 2 | 2 | 3 | 1 | 2 | 1 | 11 | Couches, traitement hebdomadaire | Format imposé (Conformist) ; un fichier par semaine, pas de logique métier propre. |
| Programmation | 1 | 2 | 3 | 1 | 1 | 1 | 9 | CRUD + ACL FilmoData | « Je programme de la même façon depuis vingt ans » ; un seul utilisateur, back-office. |
| Notification | 1 | 1 | 2 | 1 | 1 | 2 | 8 | Service du marché | Generic : on délègue au service d'envoi. |

**Ce que dit la grille** : un seul contexte justifie un DDD complet, la Vente. Les Abonnements ont un total élevé, mais le critère « experts » l'emporte : c'est le contexte que nous laissons **volontairement simple**, et nous l'assumons dans l'ADR-0001.

## 6. Vue composants

Diagramme : `docs/diagrammes/3-composants-vente` — « Comment la vente de places est-elle branchée sur le monde extérieur ? »

| Zone | Composants (noms du code) |
|---|---|
| Adapters primaires | `ReservationController` (REST, site/appli/bornes), `WebhookPayVia` + `WebhookPayViaController` (ACL PayVia), `ExpirationPlanifiee` (planificateur, chaque minute), `AclSortiesFlash` + `SortiesFlashController` (ACL partenaire), `GestionErreurs` (exceptions → HTTP) |
| Ports primaires | `ReserverDesPlaces`, `ConfirmerPaiement`, `ExpirerBlocages`, `EnregistrerVentePartenaire` |
| Services | `ReserverDesPlacesService`, `ConfirmerPaiementService`, `ExpirerBlocagesService`, `EnregistrerVentePartenaireService` |
| Domaine | Agrégats `Seance` et `Commande`, value objects (`PlaceId`, `Montant`, `Tarif`, `EtatPlace`…), événements (`CommandePayee`, `CommandeExpiree`, `PaiementARembourser`, `VentePartenaireEnregistree`) |
| Ports secondaires | `Seances`, `Commandes`, `GrilleTarifaire`, `Paiements`, `PublicateurEvenements` |
| Adapters secondaires | `SeancesEnMemoire`, `CommandesEnMemoire`, `GrilleTarifaireFixe`, `PaiementsSimules`, `PublicateurEnMemoire` |
| Composition root | `ApplicationVente` (assemble tout), `VenteConfiguration` (publie les ports comme beans Spring) |

**Comment le lire :**
- **Toutes les dépendances vont vers le centre.** Les adapters connaissent les ports ; le domaine ne connaît que Java. Seule la composition root connaît à la fois les adapters et les services.
- **Les contrôleurs ne connaissent que des ports**, jamais les services : `ReservationController` reçoit `ReserverDesPlaces`.
- **Deux ACL entrantes** traduisent les langages imposés de l'extérieur : `WebhookPayVia` (PayVia) et `AclSortiesFlash` (SortiesFlash). Leur vocabulaire ne dépasse pas leur package.
- **Ce qui a été ajouté sans toucher au cœur** : à l'étape 7, les trois adapters Spring (REST, webhook, planificateur) se sont branchés sur des ports qui existaient déjà. À l'étape 8, la vente partenaire a ajouté un port et une ACL, à côté de l'existant. Les tests des étapes 1 à 5 n'ont pas changé.

## 9. Décisions (ADR)

### ADR-0001 — Architecture retenue par contexte

| Rubrique | Contenu |
|---|---|
| **Statut** | Proposé (à valider par l'équipe) |
| **Contexte** | La billetterie compte sept contextes (section 4) pour une équipe de **deux développeurs et un prestataire**, qui doit la maintenir dix ans (C1). Faire du DDD partout coûterait trop cher ; n'en faire nulle part exposerait la Vente à un nouvel incident de mars. La grille (section 5) classe les contextes. Deux cas restent à trancher : la Tarification (décision 1) et les Abonnements (décision 2). |
| **Options envisagées** | **Décision 1 — Tarification.** A : un contexte Tarification à part, géré par le marketing, que la Vente interroge. B : le calcul du prix dans le modèle de la Vente.<br>**Décision 2 — Abonnements.** A : DDD complet (agrégat `Abonnement`, quotas, no-shows, hexagonal). B : architecture en couches, formules paramétrées, no-shows calculés par requêtes. |
| **Décision** | **Vente : hexagonal + DDD.** Seul contexte au-dessus du seuil de la grille avec un expert disponible.<br>**Décision 1 : option A, Tarification à part**, en Customer/Supplier : la Vente est cliente, via son port `GrilleTarifaire`. Les tarifs évoluent au rythme du marketing (opérations, formules en moins d'un mois), pas de la vente, et avec un autre vocabulaire (opération, supplément). Le code y est prêt : la Vente ne connaît que le port, et `GrilleTarifaireFixe` sera remplacé par un client du contexte Tarification sans toucher au cœur (c'est ce que l'étape 6 a corrigé). B6 reste dans la Vente : le prix obtenu est **figé dans le billet** au moment du choix.<br>**Décision 2 : option B, Abonnements en couches simples.** Sabine Okafor n'est disponible qu'une fois par mois : le DDD se nourrit d'échanges fréquents avec l'expert. Les formules changent si souvent qu'un paramétrage (durée, prix, quota) est plus réactif qu'un modèle codé. Les no-shows (B8) sont calculés à partir des événements « billet scanné » du Contrôle.<br>**Autres contextes** : couches simples ou CRUD (Programmation, Contrôle d'accès, Déclaration CNC), solution du marché (Notification, Paiement). |
| **Conséquences positives** | L'effort de modélisation se concentre là où est le risque (B1, pic de 420 places). Le marketing fait évoluer ses tarifs sans livraison de la Vente. Une nouvelle formule d'abonnement devient un paramétrage, compatible avec l'objectif « moins d'un mois ». Moins de code à maintenir pour une petite équipe. |
| **Conséquences négatives** | **Tarification** : une relation de plus dans la context map, et un appel à chaque réservation. Si la Tarification ne répond pas pendant une avant-première, la Vente ne peut pas fixer le prix. Parade : la Vente garde une copie locale de la grille en vigueur, rafraîchie à chaque publication du marketing.<br>**Abonnements** : les règles (B7, B8, B16) risquent de se disperser dans les services et les requêtes. Avec 1 600 abonnés et +30 % par an, la complexité va croître et une refonte vers un modèle DDD deviendra probable. Nous la préparons en isolant les Abonnements derrière une interface : la Vente ne dépend que de « cet abonné a-t-il droit à une place gratuite ? ». |

## 10. Qualité et tests

### Stratégie de tests

73 tests, tous verts, lancés par `Maven → Lifecycle → test`.

| Niveau | Ce qu'on teste | Comment | Tests |
|---|---|---|---|
| Domaine | Invariants des agrégats (B1 à B4, B6) | JUnit pur, sans Spring ni base | `etape1/SeanceTest` (13), `etape2/CommandeTest` (11) |
| Application | Orchestration des cas d'usage | Adapters en mémoire, `HorlogeReglable` pour maîtriser le temps | `etape3` à `etape5` (16), `etape8/EnregistrerVentePartenaireTest` (7) |
| ACL | Traduction du format partenaire | Java pur, sans Spring | `etape8/AclSortiesFlashTest` (6) |
| API | HTTP, codes d'erreur (201, 204, 400, 404, 409, 422) | `@SpringBootTest` + MockMvc, application complète | `etape7/ApiVenteTest` (6), `etape8/VentePartenaireApiTest` (5) |
| Acceptation | Parcours complet d'une soirée | Composition root `ApplicationVente` | `ScenarioSoireeDuJeudiTest` (2) |
| Cas limites (bonus) | Paiement en double (B14), paiement tardif (B13), deux caisses au même instant | Adapters en mémoire | `bonus/` (7) |
| Architecture | Sens des dépendances | Recherches de l'étape 6 (piste : ArchUnit) | — |

Le temps est injecté (`Clock`) : aucun test n'attend dix minutes réelles, et aucune classe du cœur n'appelle `now()` sans horloge.

### Règles de dépendance

| Couche | A le droit de dépendre de | Vérifié par la recherche |
|---|---|---|
| `domaine` | Java seulement | `.application.`, `.adapter.`, `.config.` et `import org.springframework` dans `domaine` : aucun résultat |
| `application` | `domaine` | `.adapter.`, `.config.` et `import org.springframework` dans `application` : aucun résultat |
| `adapter` | `application` (ports), `domaine` | `.now()` dans `domaine`, `application`, `adapter` : aucun résultat (l'heure vient de la `Clock` injectée) |
| `config` | tout | C'est la composition root : le seul endroit qui connaît adapters et services |

S'y ajoute une règle propre à l'étape 8 : aucun mot de SortiesFlash (`show_ref`, `ADULT`…) hors de `adapter/in/sortiesflash/`. Vérifiée par recherche : aucun résultat.

### La dépendance cachée (étape 6)

**Ce que nous avons trouvé.** Une seule recherche donnait un résultat : `.adapter.` dans `application`. `ReserverDesPlacesService` importait `adapter.out.tarification.GrilleTarifaireFixe` et le recevait dans son constructeur. Le cœur de l'hexagone dépendait d'un adapter concret : la dépendance pointait vers l'extérieur.

**Pourquoi c'est grave.** `GrilleTarifaireFixe` est provisoire : il doit être remplacé par un client du contexte Tarification (ADR-0001, décision 1). Avec cette dépendance, ce remplacement aurait obligé à modifier le service. C'est contraire à l'objectif d'évolutivité.

**Comment nous l'avons corrigé.** Le port existait déjà : `application/port/out/GrilleTarifaire`, que `GrilleTarifaireFixe` implémente. Le service dépend maintenant de ce port (champ et constructeur), et l'import de l'adapter a disparu. Le choix de l'implémentation revient à la composition root. Aucun appelant n'a changé, puisqu'un `GrilleTarifaireFixe` *est* un `GrilleTarifaire`.

**Pourquoi aucun test ne l'avait vu.** Le code était fonctionnellement correct : les prix étaient bons et les tests passaient. Le défaut porte sur le **sens des dépendances**, c'est-à-dire sur les `import`, et aucun test unitaire ne les regarde. Pour le détecter automatiquement, il faudrait un test d'architecture, par exemple ArchUnit avec la règle « aucune classe de `..application..` ne dépend de `..adapter..` ». En attendant, ce sont les recherches de l'étape 6 qui jouent ce rôle.
