package com.ecrans.vente.domaine.seance;

import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Agrégat « Séance » du contexte Vente : le plan de salle d'une séance, à vendre.
 *
 * Attention au langage : pour Julien (Programmation), une séance est un film dans une salle à une heure.
 * Ici, une séance est un ensemble de fauteuils, libres, bloqués ou vendus. Pas de film, pas de durée.
 *
 * Invariants protégés : B1 (une place, une vente), B2 (blocage de 10 minutes), B4 (vente fermée à H+15).
 */
public class Seance {

    public static final Duration DUREE_BLOCAGE = Duration.ofMinutes(10);
    public static final Duration FERMETURE_APRES_DEBUT = Duration.ofMinutes(15);

    private final SeanceId id;
    private final LocalDateTime debut;
    private final Map<PlaceId, EtatPlace> places;
    /** Verrou optimiste : lu au chargement, vérifié à l'enregistrement par l'adapter. */
    private final long version;

    private Seance(SeanceId id, LocalDateTime debut, Map<PlaceId, EtatPlace> places, long version) {
        this.id = Objects.requireNonNull(id);
        this.debut = Objects.requireNonNull(debut);
        this.places = new TreeMap<>(places);
        this.version = version;
    }

    /** Une séance publiée par la Programmation : toutes les places sont libres. */
    public static Seance ouverte(SeanceId id, LocalDateTime debut, Collection<PlaceId> plan) {
        Map<PlaceId, EtatPlace> etats = new TreeMap<>();
        plan.forEach(p -> etats.put(p, EtatPlace.libre()));
        return new Seance(id, debut, etats, 0);
    }

    /** Pour les adapters de persistance uniquement : recharger une séance enregistrée. */
    public static Seance reconstituer(SeanceId id, LocalDateTime debut, Map<PlaceId, EtatPlace> places, long version) {
        return new Seance(id, debut, places, version);
    }

    /**
     * Bloque des places pour une commande, pendant {@link #DUREE_BLOCAGE}.
     * Tout ou rien : si une seule place est prise, aucune n'est bloquée.
     *
     * @return l'heure de fin du blocage
     * @throws VenteFermee          si la vente est fermée (B4)
     * @throws PlaceInconnue        si une place n'existe pas dans la salle
     * @throws PlacesIndisponibles  si une place est déjà bloquée (blocage en cours) ou vendue (B1)
     */
    public LocalDateTime bloquer(Set<PlaceId> demandees, CommandeId commande, LocalDateTime maintenant) {
        // B4 : on ne bloque rien une fois la vente fermée
        if (!venteOuverteA(maintenant)) throw new VenteFermee(id);

        // Toutes les vérifications d'abord : la map n'est pas touchée tant qu'on n'est pas sûr (tout ou rien)
        Set<PlaceId> indisponibles = new TreeSet<>();
        for (PlaceId place : demandees) {
            EtatPlace etat = places.get(place);
            if (etat == null) throw new PlaceInconnue(id, place);
            if (!etat.estLibreA(maintenant)) indisponibles.add(place);
        }
        if (!indisponibles.isEmpty()) throw new PlacesIndisponibles(indisponibles);

        // B2 : blocage de 10 minutes
        LocalDateTime jusqua = maintenant.plus(DUREE_BLOCAGE);
        for (PlaceId place : demandees) {
            places.put(place, EtatPlace.bloquee(commande, jusqua));
        }
        return jusqua;
    }

    /**
     * Vend les places d'une commande qui vient d'être payée.
     * Chaque place doit être bloquée par cette commande… ou libre de nouveau (paiement tardif, voir B13).
     *
     * @throws PlacesIndisponibles si une place a été prise par quelqu'un d'autre entre-temps
     */
    public void vendre(CommandeId commande, Set<PlaceId> placesDeLaCommande, LocalDateTime maintenant) {
        Set<PlaceId> indisponibles = new TreeSet<>();
        for (PlaceId place : placesDeLaCommande) {
            EtatPlace etat = places.get(place);
            if (etat == null) throw new PlaceInconnue(id, place);
            // B13 : bloquée par CETTE commande (même expirée) ou de nouveau libre => vendable
            boolean vendable = etat.estBloqueePar(commande) || etat.estLibreA(maintenant);
            if (!vendable) indisponibles.add(place);
        }
        if (!indisponibles.isEmpty()) throw new PlacesIndisponibles(indisponibles);

        for (PlaceId place : placesDeLaCommande) {
            places.put(place, EtatPlace.vendue(commande));
        }
    }

    /**
     * B2 : libère toutes les places dont le blocage a expiré.
     *
     * @return les commandes dont les places ont été libérées (pour les faire expirer)
     */
    public Set<CommandeId> libererBlocagesExpires(LocalDateTime maintenant) {
        Set<CommandeId> commandes = new LinkedHashSet<>();
        for (Map.Entry<PlaceId, EtatPlace> entree : places.entrySet()) {
            EtatPlace etat = entree.getValue();
            if (etat.blocageExpireA(maintenant)) {
                commandes.add(etat.commande());
                entree.setValue(EtatPlace.libre());
            }
        }
        return commandes;
    }

    /** B4 : on vend jusqu'à 15 minutes après l'heure de début, incluse. */
    public boolean venteOuverteA(LocalDateTime maintenant) {
        return !maintenant.isAfter(debut.plus(FERMETURE_APRES_DEBUT));
    }

    public EtatPlace etat(PlaceId place) {
        EtatPlace etat = places.get(place);
        if (etat == null) throw new PlaceInconnue(id, place);
        return etat;
    }

    public Set<PlaceId> placesLibresA(LocalDateTime maintenant) {
        Set<PlaceId> libres = new TreeSet<>();
        places.forEach((p, e) -> { if (e.estLibreA(maintenant)) libres.add(p); });
        return libres;
    }

    public SeanceId id() { return id; }
    public LocalDateTime debut() { return debut; }
    public long version() { return version; }
    /** Copie : l'agrégat ne laisse personne modifier ses places de l'extérieur. */
    public Map<PlaceId, EtatPlace> places() { return new TreeMap<>(places); }

}
