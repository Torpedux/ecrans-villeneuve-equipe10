package com.ecrans.vente.adapter.in.sortiesflash;

/** La réponse attendue par SortiesFlash, dans leur format : {"partner_order_id", "cinema_order_id", "status"}. */
public record ReponseSortiesFlash(String partner_order_id, String cinema_order_id, String status) {
}
