package com.ecrans.vente.etape8;

import com.ecrans.vente.adapter.out.evenement.PublicateurEnMemoire;
import com.ecrans.vente.adapter.out.memoire.CommandesEnMemoire;
import com.ecrans.vente.adapter.out.memoire.SeancesEnMemoire;
import com.ecrans.vente.adapter.out.tarification.GrilleTarifaireFixe;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.application.service.EnregistrerVentePartenaireService;
import com.ecrans.vente.domaine.commande.Categorie;
import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commande.StatutCommande;
import com.ecrans.vente.domaine.commande.TropDePlaces;
import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;
import com.ecrans.vente.domaine.evenement.CommandePayee;
import com.ecrans.vente.domaine.evenement.VentePartenaireEnregistree;
import com.ecrans.vente.domaine.seance.PlacesIndisponibles;
import com.ecrans.vente.domaine.seance.StatutPlace;
import com.ecrans.vente.domaine.seance.VenteFermee;
import com.ecrans.vente.support.HorlogeReglable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.ecrans.vente.support.Donnees.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Étape 8 · le cas d'usage « Enregistrer une vente partenaire », avec les adapters en mémoire (B11). */
class EnregistrerVentePartenaireTest {

    private SeancesEnMemoire seances;
    private CommandesEnMemoire commandes;
    private PublicateurEnMemoire publicateur;
    private HorlogeReglable horloge;
    private EnregistrerVentePartenaireService service;

    @BeforeEach
    void preparer() {
        seances = new SeancesEnMemoire();
        commandes = new CommandesEnMemoire();
        publicateur = new PublicateurEnMemoire();
        horloge = HorlogeReglable.a("20:02");
        seances.enregistrer(seanceS42());
        service = new EnregistrerVentePartenaireService(seances, commandes, new GrilleTarifaireFixe(), publicateur, horloge);
    }

    /** G7 adulte + G8 étudiant, payés 18,40 € chez SortiesFlash (remise comprise). */
    private static VentePartenaire venteG7G8() {
        return vente(S42, "G7", Categorie.PLEIN, "G8", Categorie.REDUIT);
    }

    private static VentePartenaire vente(SeanceId seance, Object... placesEtCategories) {
        List<DemandeBillet> billets = new ArrayList<>();
        for (int i = 0; i < placesEtCategories.length; i += 2) {
            billets.add(new DemandeBillet(PlaceId.de((String) placesEtCategories[i]), (Categorie) placesEtCategories[i + 1]));
        }
        return new VentePartenaire("SortiesFlash", "SF-77812", seance, billets, Montant.centimes(1840));
    }

    @Test
    void les_places_sont_vendues_aussitot_sans_blocage() {
        service.executer(venteG7G8());

        var seance = seances.parId(S42).orElseThrow();
        assertThat(seance.etat(PlaceId.de("G7")).statut()).isEqualTo(StatutPlace.VENDUE);
        assertThat(seance.etat(PlaceId.de("G8")).statut()).isEqualTo(StatutPlace.VENDUE);
    }

    @Test
    void la_commande_est_payee_au_prix_de_notre_grille() {
        CommandeId id = service.executer(venteG7G8());

        Commande commande = commandes.parId(id).orElseThrow();
        assertThat(commande.statut()).isEqualTo(StatutCommande.PAYEE);
        assertThat(commande.total()).isEqualTo(Montant.centimes(2050)); // 11,50 € + 9,00 €
    }

    @Test
    void publie_la_commande_payee_puis_la_vente_partenaire_avec_le_montant_paye_chez_lui() {
        CommandeId id = service.executer(venteG7G8());

        assertThat(publicateur.publies()).hasSize(2);
        assertThat(publicateur.publies().get(0)).isInstanceOf(CommandePayee.class);
        assertThat(publicateur.publies().get(1)).isEqualTo(new VentePartenaireEnregistree(
                id, "SortiesFlash", "SF-77812", Montant.centimes(1840), le12Novembre("20:02")));
    }

    @Test
    void une_seule_place_prise_et_toute_la_vente_est_refusee_B1_B11() {
        service.executer(vente(S42, "G7", Categorie.PLEIN));

        assertThatThrownBy(() -> service.executer(vente(S42, "G7", Categorie.PLEIN, "G8", Categorie.REDUIT)))
                .isInstanceOf(PlacesIndisponibles.class);
        assertThat(seances.parId(S42).orElseThrow().etat(PlaceId.de("G8")).statut()).isEqualTo(StatutPlace.LIBRE);
    }

    @Test
    void plus_de_10_places_sont_refusees_B3() {
        Object[] onze = new Object[22];
        for (int n = 1; n <= 10; n++) {
            onze[2 * (n - 1)] = "G" + n;
            onze[2 * (n - 1) + 1] = Categorie.PLEIN;
        }
        onze[20] = "F1";
        onze[21] = Categorie.PLEIN;

        assertThatThrownBy(() -> service.executer(vente(S42, onze))).isInstanceOf(TropDePlaces.class);
        assertThat(publicateur.publies()).isEmpty();
    }

    @Test
    void vente_fermee_15_minutes_apres_le_debut_B4() {
        horloge.reglerA("20:46");

        assertThatThrownBy(() -> service.executer(venteG7G8())).isInstanceOf(VenteFermee.class);
    }

    @Test
    void seance_inconnue() {
        assertThatThrownBy(() -> service.executer(vente(new SeanceId("S999"), "G7", Categorie.PLEIN)))
                .isInstanceOf(SeanceInconnue.class);
    }
}
