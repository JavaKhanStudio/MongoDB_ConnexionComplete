package fr.formation.connexion.dune.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Une secousse relevee a un puits.
 *
 * <p>C'est le 1:N ILLIMITE du modele : un puits produit des releves sans
 * fin, ils ne peuvent donc pas vivre DANS le puits. Collection a part,
 * et une reference vers le puits — 62 500 documents pour 60 puits.
 */
@Document(collection = "releves")
public record Releve(
        @Id Integer id,
        String puits,
        Instant mesureLe,
        Double amplitude) {
}
