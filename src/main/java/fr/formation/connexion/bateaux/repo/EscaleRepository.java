package fr.formation.connexion.bateaux.repo;

import fr.formation.connexion.bateaux.model.Escale;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface EscaleRepository extends MongoRepository<Escale, Integer> {

    /**
     * R1 — le carnet de bord d'un bateau. Une egalite sur un champ : la
     * cle etrangere de SQL portait un index, ici rien n'est automatique.
     */
    List<Escale> findByBateau(String imo);

    /**
     * R2 — une egalite, un intervalle, un tri. {@code @Query} pour la
     * meme raison que sur les deux autres sujets : deux conditions sur
     * {@code arrivee} ne se derivent pas d'un nom de methode, et
     * {@code Between} serait exclusif des deux cotes.
     */
    @Query(value = "{ 'port' : ?0, 'arrivee' : { $gte : ?1, $lt : ?2 } }",
           sort = "{ 'arrivee' : -1 }")
    List<Escale> escalesDuPort(String port, Instant debut, Instant fin);

    /** Pour l'exploration : combien d'escales pour avarie, sur combien en tout. */
    long countByMotif(String motif);
}
