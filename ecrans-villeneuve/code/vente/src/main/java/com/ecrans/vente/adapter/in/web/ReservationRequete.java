package com.ecrans.vente.adapter.in.web;

import java.util.List;

/** Format JSON reçu par l'API : propre à l'adapter, aucun objet du domaine n'est exposé. */
public record ReservationRequete(List<BilletRequete> billets) {

    public record BilletRequete(String place, String categorie) {
    }
}
