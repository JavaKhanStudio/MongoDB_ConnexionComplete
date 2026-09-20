package fr.formation.connexion.tortues.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Un habitat. Quatorze en tout. */
@Document(collection = "habitats")
public record Habitat(
        @Id String id,
        Boolean aireProtegee) {
}
