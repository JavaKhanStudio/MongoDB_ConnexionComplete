package fr.formation.connexion.dune.service;

import fr.formation.connexion.commun.Requete;
import fr.formation.connexion.commun.Sujet;
import org.springframework.stereotype.Service;

import java.util.List;

/** Le sujet « dune », tel que le banc et les endpoints le voient. */
@Service
public class DuneSujet implements Sujet {

    private final DuneRequetes requetes;
    private final DuneOptimisation optimisation;

    public DuneSujet(DuneRequetes requetes, DuneOptimisation optimisation) {
        this.requetes = requetes;
        this.optimisation = optimisation;
    }

    @Override
    public String nom() {
        return "dune";
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
