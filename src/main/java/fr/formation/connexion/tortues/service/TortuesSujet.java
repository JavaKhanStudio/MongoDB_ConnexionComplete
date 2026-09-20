package fr.formation.connexion.tortues.service;

import fr.formation.connexion.commun.Requete;
import fr.formation.connexion.commun.Sujet;
import org.springframework.stereotype.Service;

import java.util.List;

/** Le sujet « tortues », tel que le banc et les endpoints le voient. */
@Service
public class TortuesSujet implements Sujet {

    private final TortuesRequetes requetes;
    private final TortuesOptimisation optimisation;

    public TortuesSujet(TortuesRequetes requetes, TortuesOptimisation optimisation) {
        this.requetes = requetes;
        this.optimisation = optimisation;
    }

    @Override
    public String nom() {
        return "tortues";
    }

    @Override
    public List<Requete> requetes() {
        return requetes.quatre();
    }

    @Override
    public List<Requete> requetesOptimisees() {
        return optimisation.quatreOptimisees();
    }

    @Override
    public List<String> optimiser() {
        return optimisation.poser();
    }

    @Override
    public List<String> exploration() {
        return optimisation.exploration();
    }
}
