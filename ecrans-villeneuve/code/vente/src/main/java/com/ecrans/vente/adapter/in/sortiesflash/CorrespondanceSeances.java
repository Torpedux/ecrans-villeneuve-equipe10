package com.ecrans.vente.adapter.in.sortiesflash;

import com.ecrans.vente.domaine.commun.SeanceId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Table de correspondance entre l'identifiant de séance de SortiesFlash (« VIL-20261112-2030-S3 »)
 * et le nôtre (« S42 »). En production, la Programmation l'alimenterait à chaque publication.
 */
public class CorrespondanceSeances {

    private final Map<String, SeanceId> seances = new ConcurrentHashMap<>();

    public void associer(String showRef, SeanceId seance) {
        seances.put(showRef, seance);
    }

    public Optional<SeanceId> seance(String showRef) {
        return Optional.ofNullable(seances.get(showRef));
    }
}
