package fr.formation.connexion.dune.repo;

import fr.formation.connexion.dune.model.Puits;
import org.springframework.data.mongodb.repository.MongoRepository;

/** La collection qui fait autorite sur la region d'un puits. */
public interface PuitsRepository extends MongoRepository<Puits, String> {
}
