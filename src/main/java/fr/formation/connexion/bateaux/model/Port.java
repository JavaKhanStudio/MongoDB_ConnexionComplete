package fr.formation.connexion.bateaux.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Un port. {@code quais} est un 1:N BORNE — quelques quais, et ce sera
 * tout : il reste DANS le port.
 */
@Document(collection = "ports")
public record Port(
        @Id String id,
        String pays,
        Double tirantEauMaxM,
        List<String> quais) {
}
