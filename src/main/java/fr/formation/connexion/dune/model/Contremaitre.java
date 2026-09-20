package fr.formation.connexion.dune.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Un contremaitre. 240 d'entre eux, pour qu'une egalite sur le nom soit une VRAIE egalite selective. */
@Document(collection = "contremaitres")
public record Contremaitre(
        @Id String id,
        String maison,
        Integer ancienneteAnnees) {
}
