package com.ecrans.vente.application.port.in;

import com.ecrans.vente.domaine.commun.CommandeId;

/** Port primaire : enregistrer une vente déjà payée chez un partenaire, aux mêmes règles que les nôtres (B1 à B4). */
public interface EnregistrerVentePartenaire {
    CommandeId executer(VentePartenaire vente);
}
