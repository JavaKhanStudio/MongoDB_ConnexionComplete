package fr.formation.connexion.bateaux.web;

import fr.formation.connexion.bateaux.service.BateauxSujet;
import fr.formation.connexion.commun.Banc;
import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Reference;
import fr.formation.connexion.commun.Sujet;
import fr.formation.connexion.commun.SujetController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Les sept endpoints du sujet « bateaux ». */
@RestController
@RequestMapping("/api/bateaux")
public class BateauxController extends SujetController {

    private final BateauxSujet sujet;

    public BateauxController(Banc banc, Reference reference, Bases bases, BateauxSujet sujet) {
        super(banc, reference, bases);
        this.sujet = sujet;
    }

    @Override
    protected Sujet sujet() {
        return sujet;
    }
}
