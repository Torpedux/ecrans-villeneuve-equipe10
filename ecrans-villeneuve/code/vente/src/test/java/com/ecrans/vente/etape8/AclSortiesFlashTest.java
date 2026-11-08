package com.ecrans.vente.etape8;

import com.ecrans.vente.adapter.in.sortiesflash.AclSortiesFlash;
import com.ecrans.vente.adapter.in.sortiesflash.CommandeSortiesFlash;
import com.ecrans.vente.adapter.in.sortiesflash.CorrespondanceSeances;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.domaine.commande.Categorie;
import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.commun.PlaceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ecrans.vente.support.Donnees.S42;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Étape 8 · l'ACL SortiesFlash traduit leur format dans notre langage, sans Spring. */
class AclSortiesFlashTest {

    private static final String SHOW_REF = "VIL-20261112-2030-S3";

    private AclSortiesFlash acl;

    @BeforeEach
    void preparer() {
        CorrespondanceSeances correspondance = new CorrespondanceSeances();
        correspondance.associer(SHOW_REF, S42);
        acl = new AclSortiesFlash(correspondance);
    }

    private static CommandeSortiesFlash commande(String showRef, List<String> seats, List<String> types) {
        return new CommandeSortiesFlash(showRef, seats, types, "SF-77812", 1840L);
    }

    @Test
    void traduit_la_seance_les_places_les_categories_et_garde_le_montant_paye() {
        VentePartenaire vente = acl.traduire(commande(SHOW_REF, List.of("G7", "G8", "G9"), List.of("ADULT", "STUDENT", "CHILD")));

        assertThat(vente.partenaire()).isEqualTo("SortiesFlash");
        assertThat(vente.referencePartenaire()).isEqualTo("SF-77812");
        assertThat(vente.seance()).isEqualTo(S42);
        assertThat(vente.billets()).containsExactly(
                new DemandeBillet(PlaceId.de("G7"), Categorie.PLEIN),
                new DemandeBillet(PlaceId.de("G8"), Categorie.REDUIT),
                new DemandeBillet(PlaceId.de("G9"), Categorie.ENFANT));
        assertThat(vente.montantPaye()).isEqualTo(Montant.centimes(1840));
    }

    @Test
    void seance_inconnue_chez_nous() {
        assertThatThrownBy(() -> acl.traduire(commande("VIL-19990101-2030-S3", List.of("G7"), List.of("ADULT"))))
                .isInstanceOf(SeanceInconnue.class);
    }

    @Test
    void type_de_billet_inconnu() {
        assertThatThrownBy(() -> acl.traduire(commande(SHOW_REF, List.of("G7"), List.of("VIP"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VIP");
    }

    @Test
    void pas_autant_de_types_que_de_places() {
        assertThatThrownBy(() -> acl.traduire(commande(SHOW_REF, List.of("G7", "G8"), List.of("ADULT"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void place_mal_ecrite() {
        assertThatThrownBy(() -> acl.traduire(commande(SHOW_REF, List.of("7G"), List.of("ADULT"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7G");
    }

    @Test
    void champ_obligatoire_manquant() {
        assertThatThrownBy(() -> acl.traduire(new CommandeSortiesFlash(SHOW_REF, null, List.of("ADULT"), "SF-1", 1150L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
