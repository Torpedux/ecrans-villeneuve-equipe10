package com.ecrans.vente.adapter.in.sortiesflash;

import java.util.List;

/**
 * Le JSON de SortiesFlash tel quel (annexe B), avec LEURS noms de champs.
 * C'est leur langage : il ne sort pas de ce package, l'ACL le traduit dans le nôtre.
 */
public record CommandeSortiesFlash(String show_ref, List<String> seats, List<String> ticket_types,
                                   String partner_order_id, Long paid_amount_cents) {
}
