package com.ecrans.vente.adapter.in.web;

import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.DemandeReservation;
import com.ecrans.vente.application.port.in.Reservation;
import com.ecrans.vente.application.port.in.ReserverDesPlaces;
import com.ecrans.vente.domaine.commande.Categorie;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adapter primaire REST : traduit le JSON en langage du domaine, appelle le PORT, traduit la réponse.
 * Aucune règle métier ici ; il ne connaît que le port {@link ReserverDesPlaces}, jamais le service.
 */
@RestController
public class ReservationController {

    private final ReserverDesPlaces reserverDesPlaces;

    public ReservationController(ReserverDesPlaces reserverDesPlaces) {
        this.reserverDesPlaces = reserverDesPlaces;
    }

    @PostMapping("/seances/{seance}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationReponse reserver(@PathVariable String seance, @RequestBody ReservationRequete requete) {
        List<DemandeBillet> billets = requete.billets().stream()
                .map(b -> new DemandeBillet(PlaceId.de(b.place()), Categorie.valueOf(b.categorie())))
                .toList();

        Reservation reservation = reserverDesPlaces.executer(new DemandeReservation(new SeanceId(seance), billets));

        return new ReservationReponse(
                reservation.commande().valeur(),
                reservation.total().centimes(),
                reservation.total().toString(),
                reservation.urlPaiement(),
                reservation.payerAvant());
    }
}
