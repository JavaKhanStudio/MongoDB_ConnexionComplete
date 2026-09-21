package fr.formation.connexion.bateaux.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * UN document, DEUX formes — l'heritage de SQL a disparu.
 *
 * <p>Un bateau CIVIL porte {@code armateur}, {@code typeCivil},
 * {@code portEnLourdT} et parfois {@code capaciteEvp}. Un MILITAIRE
 * porte {@code marine}, {@code classe}, {@code equipage} et
 * {@code propulsionNucleaire}. Une seule collection, un seul record, et
 * {@code categorie} qui dit quels composants sont remplis.
 *
 * <p>Spring Data sait faire autrement : {@code @TypeAlias} et une
 * hierarchie de classes, avec {@code _class} en base. On ne le fait pas
 * ici — le document est ecrit en {@code Document} brut par le
 * chargement, comme mongosh l'ecrivait, et il n'a pas de {@code _class}.
 * C'est le cas le plus frequent en vrai :
 * on se branche sur une base qui ne sait rien de Java.
 *
 * <p>{@code _id} est l'IMO : une cle metier unique, stable et deja
 * porteuse de sens. Elle FAIT la cle.
 */
@Document(collection = "bateaux")
public record Bateau(
        @Id String id,
        String nom,
        String categorie,
        String pavillon,
        Double tirantEauM,
        String portAttache,

        // categorie = CIVIL
        String armateur,
        String typeCivil,
        Integer portEnLourdT,
        Integer capaciteEvp,

        // categorie = MILITAIRE
        String marine,
        String classe,
        Integer equipage,
        Boolean propulsionNucleaire) {
}
