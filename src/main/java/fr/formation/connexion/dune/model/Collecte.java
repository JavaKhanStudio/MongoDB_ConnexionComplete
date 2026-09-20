package fr.formation.connexion.dune.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * LE DOCUMENT POLYMORPHE, en Java.
 *
 * <p>Une collecte REUSSIE porte {@code tonnes}, {@code puretePct} et
 * {@code dureeMinutes}. Une ECHOUEE porte {@code cause},
 * {@code materielPerdu}, {@code pertesHumaines} et parfois {@code ver}.
 * Les champs de l'autre forme sont ABSENTS du document — pas vides.
 *
 * <p>Cote Java, il faut bien les declarer tous : un record a le nombre
 * de composants qu'il a. Ce qui est absent en base arrive a {@code null}
 * — et ressort absent du JSON, parce que
 * {@code default-property-inclusion: non_null} est pose dans
 * {@code application.yml}. C'est la premiere chose a montrer en salle :
 * le type Java est plus rigide que le document, et on choisit ou mettre
 * la souplesse (ici : des composants nullables, et un {@code statut} qui
 * dit lesquels sont remplis).
 *
 * <p>{@code region} n'existe pas au chargement. C'est la REFERENCE
 * ETENDUE que la correction fait descendre depuis {@code puits}. Elle
 * est declaree ici pour que l'entite sache la lire une fois ecrite ; sur
 * une base au point de depart, elle vaut {@code null}.
 */
@Document(collection = "collectes")
public record Collecte(
        @Id Integer id,
        Instant debut,
        String statut,
        String puits,
        String contremaitre,
        Plateformes plateformes,

        // statut = REUSSIE
        Double tonnes,
        Double puretePct,
        Integer dureeMinutes,

        // statut = ECHOUEE
        String cause,
        Boolean materielPerdu,
        Integer pertesHumaines,
        Ver ver,

        // ajoute par la correction, absent au chargement
        String region) {

    public boolean reussie() {
        return "REUSSIE".equals(statut);
    }
}
