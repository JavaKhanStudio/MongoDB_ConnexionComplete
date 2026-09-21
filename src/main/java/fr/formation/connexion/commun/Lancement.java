package fr.formation.connexion.commun;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Les deux facons de lancer une application : servir, ou charger.
 *
 * <pre>
 *   java -jar connexion-complete-1.0.0-dune.jar                      l'API, sur 8081
 *   java -jar connexion-complete-1.0.0-dune.jar --charger            (re)fabrique la base, et s'arrete
 *   java -jar connexion-complete-1.0.0-dune.jar --charger --volume=10 --graine=20260920
 * </pre>
 *
 * <p>Avec {@code --charger}, pas de serveur web : l'application se
 * branche sur la base, la refait, et rend la main. {@code make bases}
 * joue les trois a la suite.
 */
@Component
public class Lancement implements ApplicationRunner {

    /** La graine du tirage. Meme graine, meme base, partout. */
    public static final long GRAINE = 20260920;

    private final Chargement chargement;

    public Lancement(Chargement chargement) {
        this.chargement = chargement;
    }

    /** Le {@code main} des trois applications. */
    public static void demarrer(Class<?> application, String sujet, String[] args) {
        SpringApplication app = new SpringApplication(application);
        app.setAdditionalProfiles(sujet);
        boolean charger = Arrays.asList(args).contains("--charger");
        if (charger) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        ConfigurableApplicationContext contexte = app.run(args);
        if (charger) {
            System.exit(SpringApplication.exit(contexte));
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("charger")) {
            return;
        }
        int volume = Integer.parseInt(premiere(args.getOptionValues("volume"), "1"));
        long graine = Long.parseLong(premiere(args.getOptionValues("graine"), String.valueOf(GRAINE)));
        chargement.charger(volume, graine, System.out::println);
    }

    private static String premiere(List<String> valeurs, String sinon) {
        return valeurs == null || valeurs.isEmpty() ? sinon : valeurs.get(0);
    }
}
