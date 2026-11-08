package com.ecrans.vente.domaine.evenement;

import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.Montant;

import java.time.LocalDateTime;

/** La déclaration CNC (prix payé par le spectateur) et le reversement du partenaire (B11) lisent cet événement. */
public record VentePartenaireEnregistree(CommandeId commande, String partenaire, String referencePartenaire,
                                         Montant montantPaye, LocalDateTime survenuLe) implements EvenementVente {
}
