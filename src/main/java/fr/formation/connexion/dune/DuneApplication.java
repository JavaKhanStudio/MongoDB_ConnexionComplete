package fr.formation.connexion.dune;

import fr.formation.connexion.commun.Lancement;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * POINT D'ENTREE 1 sur 3 — la base {@code dune}, sur le port 8081.
 *
 * <p>Les trois applications vivent dans le meme depot et compilent
 * ensemble, mais elles ne se melangent pas :
 * <ul>
 *   <li>{@code scanBasePackages} limite le scan a {@code commun} et au
 *       paquet du sujet. Sans ca, Spring monterait les trois jeux de
 *       services d'un coup, et {@code MongoTemplate} pointerait sur une
 *       base pour trois modeles ;</li>
 *   <li>{@code @EnableMongoRepositories} designe explicitement les
 *       repositories du sujet — declare ici, il remplace celui que
 *       l'auto-configuration aurait pose sur tout le classpath ;</li>
 *   <li>le profil {@code dune}, ajoute au demarrage, apporte le port et
 *       le nom de la base ({@code application-dune.yml}).</li>
 * </ul>
 *
 * <p>Lance avec {@code --charger}, la meme application ne sert rien :
 * elle refait la base a partir de la graine et s'arrete
 * ({@link Lancement}).
 */
@SpringBootApplication(scanBasePackages = {
        "fr.formation.connexion.commun",
        "fr.formation.connexion.dune"})
@EnableMongoRepositories(basePackages = "fr.formation.connexion.dune.repo")
public class DuneApplication {

    public static void main(String[] args) {
        Lancement.demarrer(DuneApplication.class, "dune", args);
    }
}
