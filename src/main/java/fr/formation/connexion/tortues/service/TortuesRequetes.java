package fr.formation.connexion.tortues.service;

import fr.formation.connexion.commun.Explications;
import fr.formation.connexion.commun.Requete;
import fr.formation.connexion.tortues.model.Observation;
import fr.formation.connexion.tortues.model.Tortue;
import fr.formation.connexion.tortues.repo.ObservationRepository;
import fr.formation.connexion.tortues.repo.TortueRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.g;
import static fr.formation.connexion.commun.Formats.jjmmaaaa;
import static fr.formation.connexion.commun.Formats.moyenne;

/**
 * DB 2 — Les tortues : LES QUATRE REQUETES A OPTIMISER, en Spring Data.
 *
 * <p>Portage de {@code sujets/2-tortues/requetes.js}. Ecrites de la
 * facon la plus naturelle : c'est ce qu'on ecrit quand on ne s'est pas
 * encore demande ce que le serveur allait devoir lire.
 */
@Service
public class TortuesRequetes {

    public static final String OBSERVATEUR = "Aline Roy";
    public static final String SITE = "Lady Elliot";
    public static final Instant ANNEE_DEBUT = Instant.parse("2025-01-01T00:00:00Z");
    public static final Instant ANNEE_FIN = Instant.parse("2026-01-01T00:00:00Z");
    /** « CR » : en danger critique. */
    public static final String STATUT_UICN = "CR";
    public static final Instant MOIS_DEBUT = Instant.parse("2026-07-01T00:00:00Z");
    public static final Instant MOIS_FIN = Instant.parse("2026-08-01T00:00:00Z");
    public static final String TAG = "migration-longue";

    private final ObservationRepository observations;
    private final TortueRepository tortues;
    private final MongoTemplate mongo;
    private final Explications explications;

    public TortuesRequetes(ObservationRepository observations, TortueRepository tortues,
                           MongoTemplate mongo, Explications explications) {
        this.observations = observations;
        this.tortues = tortues;
        this.mongo = mongo;
        this.explications = explications;
    }

    // =================================================================
    //  R1 — « Tout ce qu Aline Roy a observe. »
    //  Une egalite sur un champ.
    // =================================================================

    public List<String> r1() {
        return triees(observations.findByObservateur(OBSERVATEUR).stream()
                .map(TortuesRequetes::ligneR1).toList());
    }

    static String ligneR1(Observation o) {
        return d(o.id(), 8) + "  " + jjmmaaaa(o.date()) + "  " + g(o.site(), 18) + d(o.scoreSante(), 3);
    }

    // =================================================================
    //  R2 — « Les observations faites a Lady Elliot en 2025, de la plus
    //  recente a la plus ancienne. »
    //  Une egalite, un intervalle, un tri.
    // =================================================================

    public List<String> r2() {
        return triees(observations.observationsDuSite(SITE, ANNEE_DEBUT, ANNEE_FIN).stream()
                .map(TortuesRequetes::ligneR2).toList());
    }

    static String ligneR2(Observation o) {
        return jjmmaaaa(o.date()) + "  " + g(o.observateur(), 18) + " score " + d(o.scoreSante(), 3);
    }

    // =================================================================
    //  R3 — « Le score de sante moyen des tortues EN DANGER CRITIQUE
    //  observees en juillet 2026, site par site. »
    //  Le statut UICN n'est pas dans l'observation : il est dans
    //  l'espece de la tortue, recopie dans la tortue. Il faut donc
    //  ouvrir la tortue de chaque observation avant de pouvoir jeter
    //  celles qui ne sont pas d'une espece en danger critique.
    // =================================================================

    public Aggregation pipelineR3() {
        return Aggregation.newAggregation(
                Aggregation.match(Criteria.where("date")
                        .gte(Date.from(MOIS_DEBUT)).lt(Date.from(MOIS_FIN))),
                Aggregation.lookup("tortues", "tortue", "_id", "t"),
                Aggregation.match(Criteria.where("t.espece.statutUicn").is(STATUT_UICN)),
                Aggregation.group("site").sum("scoreSante").as("somme").count().as("n"));
    }

    public List<String> r3() {
        return triees(mongo.aggregate(pipelineR3(), "observations", Document.class)
                .getMappedResults().stream().map(TortuesRequetes::ligneR3).toList());
    }

    static String ligneR3(Document r) {
        long somme = ((Number) r.get("somme")).longValue();
        long n = ((Number) r.get("n")).longValue();
        return g(r.getString("_id"), 18) + " moyenne " + moyenne(somme, n, 2)
                + " sur " + d(n, 4) + " observations";
    }

    // =================================================================
    //  R4 — « Les tortues marquees migration-longue. »
    //  Le tag est dans un TABLEAU. Un index sur un tableau ne se
    //  comporte pas tout a fait comme un index sur un champ — mais il
    //  existe, et il n'y en a pas.
    // =================================================================

    public List<String> r4() {
        return triees(tortues.findByTags(TAG).stream().map(TortuesRequetes::ligneR4).toList());
    }

    static String ligneR4(Tortue t) {
        return d(t.id(), 7) + "  " + g(t.nom(), 16) + g(t.espece().nomCommun(), 20)
                + (t.habitat() != null ? t.habitat().nom() : "(sans habitat)");
    }

    // =================================================================
    //  Les quatre, mises en forme pour le banc.
    // =================================================================

    public List<Requete> quatre() {
        return List.of(
                new Requete("R1", "Les observations d un observateur",
                        this::r1,
                        () -> explications.find("observations",
                                new Document("observateur", OBSERVATEUR))),
                new Requete("R2", "Les observations d un site sur une annee",
                        this::r2,
                        () -> explications.find("observations", filtreR2(),
                                new Document("date", -1), null)),
                new Requete("R3", "Score moyen des especes en danger critique",
                        this::r3,
                        () -> explications.aggregate("observations",
                                pipelineR3().toPipeline(Aggregation.DEFAULT_CONTEXT))),
                new Requete("R4", "Les tortues portant un tag",
                        this::r4,
                        () -> explications.find("tortues", new Document("tags", TAG))));
    }

    private Document filtreR2() {
        return new Document("site", SITE)
                .append("date", new Document("$gte", Date.from(ANNEE_DEBUT))
                        .append("$lt", Date.from(ANNEE_FIN)));
    }

    static List<String> triees(List<String> lignes) {
        return lignes.stream().sorted(Comparator.naturalOrder()).toList();
    }
}
