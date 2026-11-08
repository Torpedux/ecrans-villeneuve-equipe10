package com.ecrans.vente.application.service;

import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.EnregistrerVentePartenaire;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.application.port.out.Commandes;
import com.ecrans.vente.application.port.out.GrilleTarifaire;
import com.ecrans.vente.application.port.out.PublicateurEvenements;
import com.ecrans.vente.application.port.out.Seances;
import com.ecrans.vente.domaine.commande.Billet;
import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commande.Tarif;
import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.evenement.VentePartenaireEnregistree;
import com.ecrans.vente.domaine.seance.Seance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestration seulement : une vente partenaire suit les mêmes règles que les nôtres (B1 à B4),
 * mais sans PayVia ni blocage de 10 minutes, puisque le client a déjà payé chez le partenaire (B11).
 */
public class EnregistrerVentePartenaireService implements EnregistrerVentePartenaire {

    private final Seances seances;
    private final Commandes commandes;
    private final GrilleTarifaire grille;
    private final PublicateurEvenements publicateur;
    private final Clock horloge;

    public EnregistrerVentePartenaireService(Seances seances, Commandes commandes, GrilleTarifaire grille,
                                             PublicateurEvenements publicateur, Clock horloge) {
        this.seances = seances;
        this.commandes = commandes;
        this.grille = grille;
        this.publicateur = publicateur;
        this.horloge = horloge;
    }

    @Override
    public CommandeId executer(VentePartenaire vente) {
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        CommandeId id = CommandeId.nouvelle();

        // Les billets portent le prix de NOTRE grille : c'est sur lui que se calcule le reversement (B11)
        List<Billet> billets = vente.billets().stream()
                .map(b -> new Billet(b.place(), new Tarif(b.categorie(), grille.prix(vente.seance(), b.categorie()))))
                .toList();

        // Le domaine décide, avant tout enregistrement
        Commande commande = Commande.creer(id, vente.seance(), billets, maintenant);           // B3
        Seance seance = seances.parId(vente.seance())
                .orElseThrow(() -> new SeanceInconnue(vente.seance()));
        seance.bloquer(commande.places(), id, maintenant);                                      // B4, B1, tout ou rien
        seance.vendre(id, commande.places(), maintenant);                                       // déjà payée : vendue aussitôt
        commande.payer(maintenant);

        seances.enregistrer(seance);
        commandes.enregistrer(commande);

        commande.retirerEvenements().forEach(publicateur::publier);
        publicateur.publier(new VentePartenaireEnregistree(id, vente.partenaire(), vente.referencePartenaire(),
                vente.montantPaye(), maintenant));
        return id;
    }
}
