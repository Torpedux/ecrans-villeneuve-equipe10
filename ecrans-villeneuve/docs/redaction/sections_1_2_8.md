# DAT — sections 1, 2 et 8 (brouillon Armand)

## 1. Introduction et objectifs

### Le produit
La billetterie des Écrans de Villeneuve remplace le logiciel de caisse de 2011 (support arrêté fin 2027). Elle permet de choisir, bloquer, payer et annuler des places numérotées sur cinq canaux (site, appli, bornes, guichets, partenaires). Elle garantit qu'une place n'est vendue qu'une fois (B1), gère l'abonnement, les tarifs et la déclaration CNC. Elle doit rester maintenable par deux développeurs internes et durer au moins dix ans.

### Les utilisateurs
| Utilisateur | Ce qu'il fait avec le système | Canal |
|---|---|---|
| Clients | Choisissent leurs places, paient, annulent, reçoivent leurs billets | Site, appli, bornes |
| Caissiers | Vendent pour un client présent, encaissent | Guichets |
| Abonnés | Souscrivent, réservent leur place gratuite, annulent | Site, appli, guichets |
| Agents d'accueil | Scannent les billets | Douchettes |
| Programmateur / Marketing / Finance | Publient les séances, saisissent tarifs et opérations, déposent la déclaration CNC | Back-office |
| Partenaires (SortiesFlash, CinéMalin) | Envoient des ventes déjà payées | API |

### Les objectifs de qualité
| Priorité | Objectif | Mesure | D'où vient-il ? |
|---|---|---|---|
| 1 | **Cohérence de la vente** : aucune place vendue deux fois | 0 doublon sur 420 réservations concurrentes en 5 min (test de concurrence) | Incident de mars (14 places vendues deux fois), B1 |
| 2 | **Évolutivité** : changer une règle ou brancher un partenaire sans toucher au cœur | Nouveau partenaire intégré en < 2 semaines, nouvelle formule d'abonnement en < 1 mois | H. Garnier, N. Ferrand |
| 3 | **Tenue en charge** : le pic d'avant-première ne dégrade pas la vente | Réservation répondue en < 1 s (p95) pendant le pic de 420 places en 5 min | Volumes 3.7 (avant-première, mercredi 14 h) |

## 2. Contraintes et hypothèses (hypothèses, à valider avec l'équipe)

| Question | Hypothèse retenue | Justification |
|---|---|---|
| Séance annulée : qui rembourse un billet partenaire ? | **Le partenaire** rembourse son client ; le cinéma l'en informe par événement et ne verse pas le reversement (tarif − 8 %) correspondant. | Le partenaire encaisse lui-même (B11) : nous n'avons ni le moyen de paiement ni la relation client. |
| Un abonné résilie : ses réservations du mois suivant restent-elles valables ? | **Non** : la résiliation prend effet fin de mois, les places gratuites réservées au-delà sont annulées. | B7 lie la place gratuite à un abonnement actif ; éviter des places gratuites sans abonnement payant. |
| Guichet, paiement en espèces : places bloquées 10 min ? | **Non** : le client est présent, la vente est conclue immédiatement à l'encaissement. | Le blocage (B2) sert à attendre un paiement distant ; au guichet l'attente n'existe pas. |
| Douchettes hors ligne : durée et données ? | Tenue d'**une journée** avec la liste des billets valides des séances du jour, synchronisée avant ouverture. | Couvre une panne pendant une avant-première (B12) ; un double scan en panne est accepté (K. Benali). |

(Les contraintes C1 à C6 et leurs conséquences sont à compléter avec l'équipe.)

## 8. Modèle du domaine

| Invariant (règle) | Agrégat qui le protège | Test qui le prouve |
|---|---|---|
| Une place n'est vendue qu'une fois (B1) | Seance | SeanceTest.refuse_une_place_deja_bloquee_par_un_autre_client_B1, refuse_une_place_vendue_B1 |
| Réservation tout ou rien | Seance | SeanceTest.tout_ou_rien_si_une_place_est_prise_aucune_n_est_bloquee |
| Blocage de 10 minutes, libération automatique (B2) | Seance | SeanceTest.bloque_des_places_libres_pendant_10_minutes_B2, libere_les_blocages_expires_et_renvoie_les_commandes_concernees_B2 |
| Vente fermée 15 min après le début (B4) | Seance | SeanceTest.la_vente_est_fermee_au_dela_B4 |
| Paiement tardif accepté si places libres (B13) | Seance + Commande | ConfirmerPaiementTest / bonus PaiementTardifTest |
| 10 places maximum par commande (B3) | Commande | CommandeTest.refuse_plus_de_10_places_B3 |
| Prix figé au moment du choix (B6) | Commande (Billet/Tarif) | CommandeTest.le_total_est_la_somme_des_prix_figes_dans_les_billets_B6 |
| On ne paie pas deux fois, une commande payée n'expire pas | Commande | CommandeTest.on_ne_paie_pas_deux_fois, une_commande_payee_n_expire_pas |
