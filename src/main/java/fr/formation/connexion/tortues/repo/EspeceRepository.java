package fr.formation.connexion.tortues.repo;

import fr.formation.connexion.tortues.model.Espece;
import org.springframework.data.mongodb.repository.MongoRepository;

/** La collection qui fait autorite sur le statut UICN. */
public interface EspeceRepository extends MongoRepository<Espece, String> {
}
