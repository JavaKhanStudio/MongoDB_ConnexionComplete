package fr.formation.connexion.bateaux.repo;

import fr.formation.connexion.bateaux.model.Port;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PortRepository extends MongoRepository<Port, String> {
}
