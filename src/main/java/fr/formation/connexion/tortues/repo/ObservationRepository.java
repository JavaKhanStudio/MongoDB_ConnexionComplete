package fr.formation.connexion.tortues.repo;

import fr.formation.connexion.tortues.model.Observation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ObservationRepository extends MongoRepository<Observation, Integer> {

    /**
     * R1 — {@code { observateur: ... }}
     *
     * <p>Une requete derivee, et rien de plus : c'est la requete que SQL
     * servait sans qu'on y pense, la cle etrangere vers l'observateur y
     * portant un index offert avec la contrainte. Ici, rien n'est
     * automatique — 90 000 documents balayes tant que personne n'a pose
     * l'index.
     */
    List<Observation> findByObservateur(String observateur);

    /**
     * R2 — une egalite, un intervalle, un tri.
     *
     * <p>{@code @Query} et pas une requete derivee : deux conditions sur
     * le meme champ ({@code $gte} et {@code $lt} sur {@code date}) font
     * echouer la derivation a l'execution, et {@code Between} est
     * exclusif des deux cotes alors que l'intervalle du sujet est
     * {@code [debut, fin[}. Voir le meme commentaire, en plus long, dans
     * {@code dune/repo/CollecteRepository}.
     */
    @Query(value = "{ 'site' : ?0, 'date' : { $gte : ?1, $lt : ?2 } }", sort = "{ 'date' : -1 }")
    List<Observation> observationsDuSite(String site, Instant debut, Instant fin);
}
