package fr.formation.connexion.tortues.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un programme de suivi. {@code anneeFin} et {@code especeCible} sont
 * absents quand ils n'ont pas de sens : le document est souple, le
 * record a des composants nullables.
 */
@Document(collection = "programmes")
public record Programme(
        @Id String id,
        String nom,
        String statut,
        Organisation organisation,
        Protocole protocole,
        Integer anneeFin,
        String especeCible) {

    public record Organisation(String nom, String pays) {
    }

    public record Protocole(Integer intervalleJours, String methodeMarquage) {
    }
}
