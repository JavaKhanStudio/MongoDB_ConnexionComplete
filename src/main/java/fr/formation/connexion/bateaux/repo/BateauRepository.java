package fr.formation.connexion.bateaux.repo;

import fr.formation.connexion.bateaux.model.Bateau;
import org.springframework.data.mongodb.repository.MongoRepository;

/** La collection qui fait autorite sur le pavillon d'un bateau. */
public interface BateauRepository extends MongoRepository<Bateau, String> {
}
