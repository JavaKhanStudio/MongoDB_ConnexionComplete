package fr.formation.connexion.bateaux.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Un capitaine, et la liste de ses commandements. {@code fin} absent : il commande encore. */
@Document(collection = "capitaines")
public record Capitaine(
        @Id String id,
        String brevet,
        List<Commandement> commandements) {

    public record Commandement(String bateau, Instant debut, Instant fin) {
    }
}
