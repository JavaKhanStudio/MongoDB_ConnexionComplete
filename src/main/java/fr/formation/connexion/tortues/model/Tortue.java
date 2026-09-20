package fr.formation.connexion.tortues.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Une tortue, avec ce qui a ete recopie dedans depuis « SQL vers NoSQL » :
 * l'espece et l'habitat.
 *
 * <p>{@code habitat} est ABSENT quand on ne le connait pas — pas vide,
 * pas nul en base : absent. Cote Java il arrive a {@code null}, et
 * {@code ligneR4} l'ecrit « (sans habitat) ».
 *
 * <p>{@code tags} est un tableau : MongoDB en indexe chaque element
 * separement, et l'index s'appelle MULTICLE. Une tortue a quatre tags
 * produit quatre cles — a tenir a jour a chaque ecriture.
 */
@Document(collection = "tortues")
public record Tortue(
        @Id Integer id,
        String nom,
        Espece espece,
        Mensurations mensurations,
        List<String> tags,
        List<Inscription> programmes,
        Habitat habitat) {

    /** L'espece, recopiee dans la tortue : c'est elle qui porte le statut UICN. */
    public record Espece(String nomScientifique, String nomCommun, String statutUicn) {
    }

    public record Mensurations(Integer longueurDossiereCm, Double poidsKg) {
    }

    /** L'inscription a un programme : l'acronyme, et depuis quand. */
    public record Inscription(String acronyme, Instant dateInscription) {
    }

    /** L'habitat, recopie lui aussi — deux champs, et c'est tout. */
    public record Habitat(String nom, Boolean aireProtegee) {
    }
}
