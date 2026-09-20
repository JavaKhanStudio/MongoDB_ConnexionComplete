package fr.formation.connexion.bateaux.service;

import fr.formation.connexion.commun.Requete;
import fr.formation.connexion.commun.Sujet;
import org.springframework.stereotype.Service;

import java.util.List;

/** Le sujet « bateaux », tel que le banc et les endpoints le voient. */
@Service
public class BateauxSujet implements Sujet {

    private final BateauxRequetes requetes;
    private final BateauxOptimisation optimisation;

    public BateauxSujet(BateauxRequetes requetes, BateauxOptimisation optimisation) {
        this.requetes = requetes;
        this.optimisation = optimisation;
    }

    @Override
    public String nom() {
        return "bateaux";
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
