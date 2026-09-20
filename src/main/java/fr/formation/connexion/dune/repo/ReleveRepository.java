package fr.formation.connexion.dune.repo;

import fr.formation.connexion.dune.model.Releve;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReleveRepository extends MongoRepository<Releve, Integer> {

    /**
     * R4 — les dix plus fortes secousses d'un puits.
     *
     * <p>{@code Top10} pose le {@code limit(10)}, et les deux
     * {@code Desc} posent le tri. Dix lignes en sortie : sans index, le
     * serveur lit quand meme les 62 500 releves, garde les 1 027 du
     * puits, les trie en memoire et en jette 1 017. L'etage TRI du plan,
     * c'est ca — il ne peut rien rendre avant d'avoir tout vu.
     */
    List<Releve> findTop10ByPuitsOrderByAmplitudeDescMesureLeDesc(String puits);
}
