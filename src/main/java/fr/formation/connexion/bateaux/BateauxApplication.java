package fr.formation.connexion.bateaux;

import fr.formation.connexion.commun.Lancement;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * POINT D'ENTREE 3 sur 3 — la base {@code bateaux}, sur le port 8083.
 * Meme montage que les deux autres, autre paquet, autre profil.
 */
@SpringBootApplication(scanBasePackages = {
        "fr.formation.connexion.commun",
        "fr.formation.connexion.bateaux"})
@EnableMongoRepositories(basePackages = "fr.formation.connexion.bateaux.repo")
public class BateauxApplication {

    public static void main(String[] args) {
        Lancement.demarrer(BateauxApplication.class, "bateaux", args);
    }
}
