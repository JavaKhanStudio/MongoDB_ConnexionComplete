package fr.formation.connexion.dune.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un puits. Son {@code _id} est son code — « HAB-02 », pas un ObjectId :
 * quand la cle metier est deja unique et stable, elle FAIT la cle.
 *
 * <p>C'est {@code region} qui interesse R3 : le puits sait ou il est, la
 * collecte non. La collection {@code puits} reste celle qui fait
 * autorite ; la correction en recopie {@code region} dans la collecte,
 * et nulle part ailleurs.
 */
@Document(collection = "puits")
public record Puits(
        @Id String id,
        String region,
        Boolean epuise,
        Integer profondeurM) {
}
