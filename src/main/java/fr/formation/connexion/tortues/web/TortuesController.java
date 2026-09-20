package fr.formation.connexion.tortues.web;

import fr.formation.connexion.commun.Banc;
import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Reference;
import fr.formation.connexion.commun.Sujet;
import fr.formation.connexion.commun.SujetController;
import fr.formation.connexion.tortues.service.TortuesSujet;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Les sept endpoints du sujet « tortues ». */
@RestController
@RequestMapping("/api/tortues")
public class TortuesController extends SujetController {

    private final TortuesSujet sujet;

    public TortuesController(Banc banc, Reference reference, Bases bases, TortuesSujet sujet) {
        super(banc, reference, bases);
        this.sujet = sujet;
    }

    @Override
    protected Sujet sujet() {
        return sujet;
    }
}
