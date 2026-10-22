package com.ecrans.vente.application.service;

import com.ecrans.vente.application.exception.CommandeInconnue;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.ConfirmerPaiement;
import com.ecrans.vente.application.port.out.Commandes;
import com.ecrans.vente.application.port.out.PublicateurEvenements;
import com.ecrans.vente.application.port.out.Seances;
import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commande.StatutCommande;
import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.seance.PlacesIndisponibles;
import com.ecrans.vente.domaine.seance.Seance;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Le paiement est confirmé : la commande est payée ET ses places sont vendues, dans la même transaction.
 * C'est l'entorse consciente à la règle 4 de Vernon (ADR-0002) : « payée ⇒ vendue » doit être immédiat,
 * sinon le blocage pourrait expirer et la place être revendue entre les deux.
 */
public class ConfirmerPaiementService implements ConfirmerPaiement {

    private final Seances seances;
    private final Commandes commandes;
    private final PublicateurEvenements publicateur;
    private final Clock horloge;

    public ConfirmerPaiementService(Seances seances, Commandes commandes, PublicateurEvenements publicateur,
                                    Clock horloge) {
        this.seances = seances;
        this.commandes = commandes;
        this.publicateur = publicateur;
        this.horloge = horloge;
    }

    @Override
    public void executer(CommandeId id) {
        Commande commande = commandes.parId(id)
                .orElseThrow(() -> new CommandeInconnue(id));

        // Bonus B14 : idempotence des notifications de paiement (au moins une fois)
        if (commande.statut() == StatutCommande.PAYEE || commande.statut() == StatutCommande.A_REMBOURSER) {
            return;
        }

        Seance seance = seances.parId(commande.seance())
                .orElseThrow(() -> new SeanceInconnue(commande.seance()));

        LocalDateTime maintenant = LocalDateTime.now(horloge);

        try {
            seance.vendre(id, commande.places(), maintenant);
            commande.payer(maintenant);
            seances.enregistrer(seance);
        } catch (PlacesIndisponibles e) {
            // Bonus B13 : places reprises entre-temps, remboursement immédiat
            commande.aRembourser(maintenant);
        }

        commandes.enregistrer(commande);
        commande.retirerEvenements().forEach(publicateur::publier);
    }
}
