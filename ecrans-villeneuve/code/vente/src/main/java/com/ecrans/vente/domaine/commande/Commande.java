package com.ecrans.vente.domaine.commande;

import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;
import com.ecrans.vente.domaine.evenement.CommandeExpiree;
import com.ecrans.vente.domaine.evenement.CommandePayee;
import com.ecrans.vente.domaine.evenement.EvenementVente;
import com.ecrans.vente.domaine.evenement.PaiementARembourser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Agrégat « Commande » : 1 à 10 billets d'une même séance, payés en une fois.
 * Référence la séance par son identité (règle 3 de Vernon), jamais par l'objet Seance.
 *
 * Invariants protégés : B3 (10 places max), B6 (prix figé dans les billets), cycle de vie du paiement.
 */
public class Commande {

    public static final int MAX_PLACES = 10;

    private final CommandeId id;
    private final SeanceId seance;
    private final List<Billet> billets;
    private final LocalDateTime expireA;
    private StatutCommande statut;
    private final List<EvenementVente> evenements = new ArrayList<>();

    private Commande(CommandeId id, SeanceId seance, List<Billet> billets, LocalDateTime expireA, StatutCommande statut) {
        this.id = Objects.requireNonNull(id);
        this.seance = Objects.requireNonNull(seance);
        this.billets = List.copyOf(billets);
        this.expireA = Objects.requireNonNull(expireA);
        this.statut = Objects.requireNonNull(statut);
    }

    /**
     * Crée une commande en attente de paiement.
     *
     * @throws CommandeVide  si aucun billet
     * @throws TropDePlaces  au-delà de {@link #MAX_PLACES} billets (B3)
     * @throws PlaceEnDouble si une place apparaît deux fois
     */
    public static Commande creer(CommandeId id, SeanceId seance, List<Billet> billets, LocalDateTime expireA) {
        if (billets.isEmpty()) {
            throw new CommandeVide();
        }
        if (billets.size() > MAX_PLACES) {
            throw new TropDePlaces(billets.size());
        }
        Set<PlaceId> placesVues = new HashSet<>();
        for (Billet billet : billets) {
            if (!placesVues.add(billet.place())) {
                throw new PlaceEnDouble(billet.place());
            }
        }
        return new Commande(id, seance, billets, expireA, StatutCommande.EN_ATTENTE_PAIEMENT);
    }

    /** Pour les adapters de persistance uniquement. */
    public static Commande reconstituer(CommandeId id, SeanceId seance, List<Billet> billets, LocalDateTime expireA,
                                       StatutCommande statut) {
        return new Commande(id, seance, billets, expireA, statut);
    }

    /** B6 : la somme des prix figés dans les billets. */
    public Montant total() {
        Montant total = Montant.ZERO;
        for (Billet billet : billets) {
            total = total.plus(billet.tarif().prix());
        }
        return total;
    }

    /**
     * Le paiement est confirmé : la commande devient PAYEE et enregistre l'événement {@link CommandePayee}.
     * Possible depuis EN_ATTENTE_PAIEMENT, et depuis EXPIREE (paiement tardif, voir B13).
     *
     * @throws TransitionInterdite depuis tout autre statut
     */
    public void payer(LocalDateTime maintenant) {
        if (statut != StatutCommande.EN_ATTENTE_PAIEMENT && statut != StatutCommande.EXPIREE) {
            throw new TransitionInterdite(id, statut, "payer");
        }
        statut = StatutCommande.PAYEE;
        evenements.add(new CommandePayee(id, seance, places(), total(), maintenant));
    }

    /**
     * Personne n'a payé à temps : la commande devient EXPIREE et enregistre {@link CommandeExpiree}.
     *
     * @throws TransitionInterdite si la commande n'est pas EN_ATTENTE_PAIEMENT
     */
    public void expirer(LocalDateTime maintenant) {
        if (statut != StatutCommande.EN_ATTENTE_PAIEMENT) {
            throw new TransitionInterdite(id, statut, "faire expirer");
        }
        statut = StatutCommande.EXPIREE;
        evenements.add(new CommandeExpiree(id, seance, maintenant));
    }

    /**
     * Bonus B13 : payée trop tard et places reprises, ou séance annulée (B10).
     * La commande passe à A_REMBOURSER et enregistre l'événement {@link PaiementARembourser}.
     *
     * @throws TransitionInterdite si la commande n'est ni EXPIREE ni PAYEE
     */
    public void aRembourser(LocalDateTime maintenant) {
        if (statut != StatutCommande.EXPIREE && statut != StatutCommande.PAYEE) {
            throw new TransitionInterdite(id, statut, "rembourser");
        }
        statut = StatutCommande.A_REMBOURSER;
        evenements.add(new PaiementARembourser(id, total(), maintenant));
    }

    /** Les événements enregistrés depuis le chargement ; l'application les publie après l'enregistrement. */
    public List<EvenementVente> retirerEvenements() {
        List<EvenementVente> copie = List.copyOf(evenements);
        evenements.clear();
        return copie;
    }

    public Set<PlaceId> places() {
        Set<PlaceId> places = new TreeSet<>();
        billets.forEach(b -> places.add(b.place()));
        return places;
    }

    public CommandeId id() { return id; }
    public SeanceId seance() { return seance; }
    public List<Billet> billets() { return billets; }
    public LocalDateTime expireA() { return expireA; }
    public StatutCommande statut() { return statut; }
}
