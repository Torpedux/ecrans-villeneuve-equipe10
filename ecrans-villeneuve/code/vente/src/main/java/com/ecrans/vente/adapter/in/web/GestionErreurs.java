package com.ecrans.vente.adapter.in.web;

import com.ecrans.vente.application.exception.CommandeInconnue;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.out.ConflitDeVersion;
import com.ecrans.vente.domaine.commun.ErreurMetier;
import com.ecrans.vente.domaine.seance.PlacesIndisponibles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Adapter primaire : traduit les exceptions du cœur en codes HTTP.
 * Spring choisit toujours le handler le plus précis : PlacesIndisponibles est une ErreurMetier,
 * mais c'est bien le 409 qui gagne sur le 422.
 */
@RestControllerAdvice
public class GestionErreurs {

    /** 409 : la place vient d'être prise par quelqu'un d'autre (B1). */
    @ExceptionHandler({PlacesIndisponibles.class, ConflitDeVersion.class})
    ProblemDetail conflit(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** 404 : ce qu'on demande n'existe pas. */
    @ExceptionHandler({SeanceInconnue.class, CommandeInconnue.class})
    ProblemDetail introuvable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 422 : demande bien écrite, mais une règle métier la refuse (VenteFermee, TropDePlaces…). */
    @ExceptionHandler(ErreurMetier.class)
    ProblemDetail regleMetier(ErreurMetier e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /** 400 : demande mal écrite (place « 7G », catégorie inconnue). */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail demandeMalEcrite(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
