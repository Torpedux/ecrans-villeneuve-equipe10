# DAT — section 10 (brouillon Aurel)

## 10. Qualité et tests

### Vérification de l'architecture (étape 6)

Nous avons vérifié la règle de dépendance de l'hexagone (domaine → rien ; application → domaine ; adapters → application ; config → tout) en recherchant, dossier par dossier, les imports interdits :

| Dossier | Recherche | Résultat avant correction | Après |
|---|---|---|---|
| `domaine` | `.application.`, `.adapter.`, `.config.` | aucun | aucun |
| `domaine`, `application` | `import org.springframework` | aucun | aucun |
| `application` | `.adapter.`, `.config.` | **1 résultat** | aucun |
| `domaine`, `application`, `adapter` | `.now()` | aucun | aucun |

**Ce que nous avons trouvé.** `application/service/ReserverDesPlacesService` importait `adapter.out.tarification.GrilleTarifaireFixe` et la recevait dans son constructeur. Le service applicatif, au cœur de l'hexagone, dépendait donc d'un adapter secondaire concret : la dépendance pointait vers l'extérieur, dans le mauvais sens.

**Pourquoi c'est un problème.** `GrilleTarifaireFixe` est un adapter provisoire, destiné à être remplacé par un client du contexte Tarification. Avec cette dépendance, on ne pouvait pas le remplacer (ni par le vrai client, ni par un double de test) sans modifier le service. Cela va contre l'objectif de qualité n° 2 (évolutivité : changer un adapter sans toucher au cœur).

**Comment nous l'avons corrigé.** Le port existait déjà : `application/port/out/GrilleTarifaire`, l'interface que `GrilleTarifaireFixe` implémente. Le service dépend maintenant de `GrilleTarifaire` (champ et paramètre du constructeur), et l'import de l'adapter a disparu. Le choix de l'implémentation concrète revient à la composition root (`config/ApplicationVente`, puis la configuration Spring), le seul endroit qui connaît à la fois adapters et services. Aucun appelant n'a dû changer, puisqu'un `GrilleTarifaireFixe` est un `GrilleTarifaire`.

**Pourquoi aucun test ne l'avait vu.** Le code était fonctionnellement correct : les prix calculés étaient bons et les 42 tests passaient. Le défaut porte sur le sens des dépendances, c'est-à-dire sur les `import`, et aucun test unitaire ou d'acceptation ne les regarde. Pour le détecter automatiquement, il faudrait un test d'architecture, par exemple ArchUnit avec une règle « aucune classe de `..application..` ne dépend de `..adapter..` ». En attendant, ce sont les recherches manuelles de l'étape 6 qui jouent ce rôle.

### Stratégie de tests

| Niveau | Ce qu'on teste | Comment |
|---|---|---|
| Domaine | Invariants des agrégats (B1 à B4, B6) | `etape1/SeanceTest`, `etape2/CommandeTest`, JUnit pur |
| Application | Orchestration des cas d'usage | `etape3` à `etape5`, adapters en mémoire, `HorlogeReglable` |
| ACL | Traduction du format partenaire | Tests de l'étape 8, sans Spring |
| API | HTTP, codes d'erreur | `ApiVenteTest`, `VentePartenaireApiTest` (`@SpringBootTest`) |
| Acceptation | Parcours complet | `ScenarioSoireeDuJeudiTest` |
| Architecture | Sens des dépendances | Recherches de l'étape 6 (piste : ArchUnit) |
