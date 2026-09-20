package fr.formation.connexion.dune.web;

import fr.formation.connexion.commun.Banc;
import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Reference;
import fr.formation.connexion.commun.Sujet;
import fr.formation.connexion.commun.SujetController;
import fr.formation.connexion.dune.service.DuneSujet;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les sept endpoints du sujet « dune ». Tout le comportement est dans
 * {@link SujetController} : les trois applications repondent exactement
 * la meme chose, sur trois bases et trois ports differents.
 */
@RestController
@RequestMapping("/api/dune")
public class DuneController extends SujetController {

    private final DuneSujet sujet;

    public DuneController(Banc banc, Reference reference, Bases bases, DuneSujet sujet) {
        super(banc, reference, bases);
        this.sujet = sujet;
    }

    @Override
    protected Sujet sujet() {
        return sujet;
    }
}
