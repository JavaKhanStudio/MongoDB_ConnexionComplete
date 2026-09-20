package fr.formation.connexion.tortues.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Un site d'observation, et l'habitat dont il fait partie. */
@Document(collection = "sites")
public record Site(
        @Id String id,
        String habitat) {
}
