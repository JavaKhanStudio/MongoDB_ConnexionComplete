package fr.formation.connexion.tortues;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * POINT D'ENTREE 2 sur 3 — la base {@code tortues}, sur le port 8082.
 * Meme montage que {@code DuneApplication}, autre paquet, autre profil.
 */
@SpringBootApplication(scanBasePackages = {
        "fr.formation.connexion.commun",
        "fr.formation.connexion.tortues"})
@EnableMongoRepositories(basePackages = "fr.formation.connexion.tortues.repo")
public class TortuesApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TortuesApplication.class);
        app.setAdditionalProfiles("tortues");
        app.run(args);
    }
}
