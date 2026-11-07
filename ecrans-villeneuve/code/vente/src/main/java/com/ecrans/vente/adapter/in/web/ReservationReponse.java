package com.ecrans.vente.adapter.in.web;

import java.time.LocalDateTime;

/** Format JSON renvoyé par l'API après une réservation. */
public record ReservationReponse(String commande, long totalCentimes, String total,
                                 String urlPaiement, LocalDateTime payerAvant) {
}
