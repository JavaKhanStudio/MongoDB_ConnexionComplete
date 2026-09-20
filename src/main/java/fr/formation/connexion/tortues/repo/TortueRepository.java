package fr.formation.connexion.tortues.repo;

import fr.formation.connexion.tortues.model.Tortue;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface TortueRepository extends MongoRepository<Tortue, Integer> {

    /**
     * R4 — « les tortues marquees migration-longue ».
     *
     * <p>{@code tags} est un TABLEAU, et pourtant le parametre est une
     * chaine : MongoDB compare une valeur a chaque element du tableau
     * sans qu'on ait rien a dire. Cote Spring Data, {@code findByTags}
     * suffit — il n'y a pas de mot-cle « contient » a chercher.
     */
    List<Tortue> findByTags(String tag);

    // -----------------------------------------------------------------
    //  L'exploration : le tableau de sous-documents, et $elemMatch
    // -----------------------------------------------------------------

    /**
     * LE GESTE NATUREL, et il est faux. Deux conditions posees a plat
     * sur {@code programmes} s'appliquent au TABLEAU, pas a un MEME
     * element : une tortue inscrite a ARGOI en 2019 et a PNTL en 2025
     * les satisfait toutes les deux, chacune par un element different.
     */
    @Query(value = "{ 'programmes.acronyme' : ?0, 'programmes.dateInscription' : { $gte : ?1 } }",
           count = true)
    long compterNaif(String acronyme, Instant depuis);

    /**
     * Celle qui repond : {@code $elemMatch} exige que ce soit le meme
     * element qui satisfasse les deux conditions — et il resserre du
     * meme coup les bornes de l'index multicle.
     */
    @Query(value = "{ 'programmes' : { $elemMatch : { 'acronyme' : ?0, 'dateInscription' : { $gte : ?1 } } } }",
           count = true)
    long compterAvecElemMatch(String acronyme, Instant depuis);
}
