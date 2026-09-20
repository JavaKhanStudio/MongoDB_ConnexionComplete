package fr.formation.connexion.tortues.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Une observation : une tortue, vue a un site, un jour, par quelqu'un.
 *
 * <p>LE SEUL CHANGEMENT DE MODELE depuis « SQL vers NoSQL », et il est
 * assume. La-bas, les 727 observations etaient DANS la tortue, et
 * c'etait le bon choix : cinq par document, une campagne par an. A
 * 10 000 tortues suivies pendant huit ans, le tableau ne s'arrete jamais
 * de grossir. C'est le 1:N non borne, et il sort.
 *
 * <p>{@code statutUicn} n'existe pas au chargement : c'est la reference
 * etendue que la correction fait descendre depuis
 * {@code tortues.espece.statutUicn} — une COPIE DE COPIE, puisque la
 * tortue tient deja la sienne de {@code especes}.
 */
@Document(collection = "observations")
public record Observation(
        @Id Integer id,
        Integer tortue,
        String site,
        String observateur,
        Instant date,
        Integer scoreSante,

        // ajoute par la correction, absent au chargement
        String statutUicn) {
}
