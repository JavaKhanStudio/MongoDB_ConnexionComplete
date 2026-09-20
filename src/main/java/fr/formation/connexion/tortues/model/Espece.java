package fr.formation.connexion.tortues.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * L'espece, collection a part : c'est elle qui FAIT AUTORITE sur le
 * statut UICN. Il est recopie dans la tortue, et la correction le
 * recopie une fois de plus dans l'observation.
 *
 * <p>Le jour ou l'UICN reclasse une espece, il y a donc TROIS endroits a
 * reecrire, et ils ne le seront pas au meme instant. On accepte ca parce
 * qu'une reclassification arrive tous les dix ans et que R3 est posee
 * tous les jours.
 */
@Document(collection = "especes")
public record Espece(
        @Id String id,
        String nomCommun,
        String statutUicn) {
}
