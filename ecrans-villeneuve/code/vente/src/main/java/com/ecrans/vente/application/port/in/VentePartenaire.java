package com.ecrans.vente.application.port.in;

import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.commun.SeanceId;

import java.util.List;

/**
 * Une vente déjà payée chez un partenaire (B11), traduite dans NOTRE langage par l'ACL du partenaire.
 * Le montant payé chez lui n'est pas celui de notre grille : on le garde pour la déclaration CNC et le reversement.
 */
public record VentePartenaire(String partenaire, String referencePartenaire, SeanceId seance,
                              List<DemandeBillet> billets, Montant montantPaye) {
    public VentePartenaire {
        billets = List.copyOf(billets);
    }
}
