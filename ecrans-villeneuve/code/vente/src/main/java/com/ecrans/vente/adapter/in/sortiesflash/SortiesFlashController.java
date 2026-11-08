package com.ecrans.vente.adapter.in.sortiesflash;

import com.ecrans.vente.application.port.in.EnregistrerVentePartenaire;
import com.ecrans.vente.domaine.commun.CommandeId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adapter primaire REST pour SortiesFlash : confie leur JSON à l'ACL, appelle le PORT, répond dans leur format.
 * Les refus passent par GestionErreurs : 409 place prise, 404 séance inconnue, 400 format invalide.
 */
@RestController
public class SortiesFlashController {

    private final AclSortiesFlash acl;
    private final EnregistrerVentePartenaire enregistrerVentePartenaire;

    public SortiesFlashController(AclSortiesFlash acl, EnregistrerVentePartenaire enregistrerVentePartenaire) {
        this.acl = acl;
        this.enregistrerVentePartenaire = enregistrerVentePartenaire;
    }

    @PostMapping("/partner/v2/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ReponseSortiesFlash commander(@RequestBody CommandeSortiesFlash commande) {
        CommandeId id = enregistrerVentePartenaire.executer(acl.traduire(commande));
        return new ReponseSortiesFlash(commande.partner_order_id(), id.valeur(), "CONFIRMED");
    }
}
