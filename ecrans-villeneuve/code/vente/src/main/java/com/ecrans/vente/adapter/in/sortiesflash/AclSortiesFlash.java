package com.ecrans.vente.adapter.in.sortiesflash;

import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.domaine.commande.Categorie;
import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anti-corruption layer : traduit le format de SortiesFlash dans le langage de la Vente.
 * Java pur, sans Spring. Si SortiesFlash change un code, seule cette classe bouge ;
 * brancher CinéMalin, ce sera une seconde ACL, et rien d'autre.
 */
public class AclSortiesFlash {

    static final String PARTENAIRE = "SortiesFlash";

    private static final Map<String, Categorie> CATEGORIES = Map.of(
            "ADULT", Categorie.PLEIN,
            "STUDENT", Categorie.REDUIT,
            "CHILD", Categorie.ENFANT);

    private final CorrespondanceSeances correspondance;

    public AclSortiesFlash(CorrespondanceSeances correspondance) {
        this.correspondance = correspondance;
    }

    public VentePartenaire traduire(CommandeSortiesFlash commande) {
        if (commande.seats() == null || commande.ticket_types() == null || commande.paid_amount_cents() == null) {
            throw new IllegalArgumentException("Commande SortiesFlash incomplète : seats, ticket_types et paid_amount_cents sont obligatoires");
        }
        if (commande.seats().size() != commande.ticket_types().size()) {
            throw new IllegalArgumentException("Commande SortiesFlash invalide : " + commande.seats().size()
                    + " places pour " + commande.ticket_types().size() + " types de billet");
        }

        SeanceId seance = correspondance.seance(commande.show_ref())
                .orElseThrow(() -> new SeanceInconnue(new SeanceId(String.valueOf(commande.show_ref()))));

        List<DemandeBillet> billets = new ArrayList<>();
        for (int i = 0; i < commande.seats().size(); i++) {
            billets.add(new DemandeBillet(place(commande.seats().get(i)), categorie(commande.ticket_types().get(i))));
        }

        return new VentePartenaire(PARTENAIRE, commande.partner_order_id(), seance, billets,
                Montant.centimes(commande.paid_amount_cents()));
    }

    private static PlaceId place(String seat) {
        try {
            return PlaceId.de(seat);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Place SortiesFlash mal écrite : " + seat);
        }
    }

    private static Categorie categorie(String ticketType) {
        Categorie categorie = CATEGORIES.get(ticketType);
        if (categorie == null) {
            throw new IllegalArgumentException("Type de billet SortiesFlash inconnu : " + ticketType);
        }
        return categorie;
    }
}
