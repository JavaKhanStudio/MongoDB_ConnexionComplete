package fr.formation.connexion.dune.service;

import fr.formation.connexion.commun.Explications;
import fr.formation.connexion.commun.Requete;
import fr.formation.connexion.dune.model.Collecte;
import fr.formation.connexion.dune.model.Releve;
import fr.formation.connexion.dune.repo.CollecteRepository;
import fr.formation.connexion.dune.repo.ReleveRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.deci;
import static fr.formation.connexion.commun.Formats.g;
import static fr.formation.connexion.commun.Formats.jjmmaaaa;

/**
 * DB 1 — « Dune » : LES QUATRE REQUETES A OPTIMISER, en Spring Data.
 *
 * <p>C'est le portage de {@code sujets/1-dune/requetes.js}. Elles sont
 * ecrites de la facon la plus naturelle : c'est ce qu'on ecrit quand on
 * ne s'est pas encore demande ce que le serveur allait devoir lire.
 *
 * <p>LA SEULE REGLE : la REPONSE ne change pas. Elle a ete calculee au
 * chargement mongosh, a part, a partir des donnees generees — pas en
 * rejouant ces requetes-ci. Le banc la recompare a chaque passage. Le
 * chemin t'appartient, la reponse non.
 */
@Service
public class DuneRequetes {

    /**
     * Les valeurs sur lesquelles les quatre requetes portent. Ce sont
     * celles de {@code requetes.js} : le chargement s'en est servi pour
     * calculer la reponse attendue, les changer ici ferait mentir la
     * verification.
     */
    public static final String CONTREMAITRE = "Gurney Halleck";
    public static final String PUITS_ECHECS = "HAB-02";
    public static final String REGION = "Erg Habbanya";
    public static final Instant MOIS_DEBUT = Instant.parse("2026-07-01T00:00:00Z");
    public static final Instant MOIS_FIN = Instant.parse("2026-08-01T00:00:00Z");
    public static final String PUITS_PICS = "MUR-01";

    private final CollecteRepository collectes;
    private final ReleveRepository releves;
    private final MongoTemplate mongo;
    private final Explications explications;

    public DuneRequetes(CollecteRepository collectes, ReleveRepository releves,
                        MongoTemplate mongo, Explications explications) {
        this.collectes = collectes;
        this.releves = releves;
        this.mongo = mongo;
        this.explications = explications;
    }

    // =================================================================
    //  R1 — « Tout ce que Gurney Halleck a sorti. »
    //  Une egalite sur un champ, et rien d'autre. C'est la requete que
    //  SQL servait sans qu'on y pense : la cle etrangere y portait un
    //  index, offert avec la contrainte.
    // =================================================================

    public List<String> r1() {
        return triees(collectes.findByContremaitre(CONTREMAITRE).stream().map(DuneRequetes::ligneR1).toList());
    }

    private org.bson.Document filtreR1() {
        return new org.bson.Document("contremaitre", CONTREMAITRE);
    }

    static String ligneR1(Collecte c) {
        return d(c.id(), 7) + "  " + jjmmaaaa(c.debut()) + "  " + g(c.puits(), 9) + g(c.statut(), 9)
                + (c.reussie() ? d(deci(c.tonnes(), 2), 8) + " t" : d(c.cause(), 10));
    }

    // =================================================================
    //  R2 — « Les echecs du puits HAB-02, du plus recent au plus
    //  ancien. »
    //  Une egalite, un intervalle, un tri. Trois choses, et l'ordre dans
    //  lequel on les met dans un index n'est pas indifferent.
    // =================================================================

    public List<String> r2() {
        return triees(collectes.echecsDuPuits(PUITS_ECHECS, "ECHOUEE", MOIS_DEBUT, MOIS_FIN)
                .stream().map(DuneRequetes::ligneR2).toList());
    }

    private org.bson.Document filtreR2() {
        return new org.bson.Document("puits", PUITS_ECHECS)
                .append("statut", "ECHOUEE")
                .append("debut", new org.bson.Document("$gte", Date.from(MOIS_DEBUT))
                        .append("$lt", Date.from(MOIS_FIN)));
    }

    static String ligneR2(Collecte c) {
        return jjmmaaaa(c.debut()) + "  " + g(c.cause(), 11) + d(c.pertesHumaines(), 3) + " morts"
                + (Boolean.TRUE.equals(c.materielPerdu()) ? ",  materiel perdu" : "");
    }

    // =================================================================
    //  R3 — « Ce que la region Erg Habbanya a sorti en juillet 2026,
    //  puits par puits. »
    //  La region n'est PAS dans la collecte : elle est dans le puits. Il
    //  faut donc aller la chercher — pour les 37 500 collectes — avant
    //  de pouvoir jeter celles qui ne sont pas de cette region.
    // =================================================================

    /**
     * Le pipeline, avec l'API {@code Aggregation} de Spring Data.
     *
     * <p>Les dates passent en {@link Date} et non en {@link Instant} :
     * une agregation NON TYPEE ({@code newAggregation} sans classe
     * d'entree) n'applique pas le convertisseur de Spring Data, le
     * pipeline part tel quel au driver. C'est le prix de la fidelite —
     * ce pipeline-ci est, au caractere pres, celui de
     * {@code requetes.js}.
     */
    public Aggregation pipelineR3() {
        return Aggregation.newAggregation(
                Aggregation.match(Criteria.where("statut").is("REUSSIE")
                        .and("debut").gte(Date.from(MOIS_DEBUT)).lt(Date.from(MOIS_FIN))),
                Aggregation.lookup("puits", "puits", "_id", "p"),
                Aggregation.match(Criteria.where("p.region").is(REGION)),
                Aggregation.group("puits").sum("tonnes").as("tonnes").count().as("n"));
    }

    public List<String> r3() {
        return triees(mongo.aggregate(pipelineR3(), "collectes", org.bson.Document.class)
                .getMappedResults().stream().map(DuneRequetes::ligneR3).toList());
    }

    static String ligneR3(org.bson.Document r) {
        return g(r.getString("_id"), 9) + d(deci(nombre(r, "tonnes"), 2), 11)
                + " t en " + d(r.get("n"), 4) + " collectes";
    }

    // =================================================================
    //  R4 — « Les dix plus fortes secousses relevees au puits MUR-01. »
    //  Dix lignes en sortie. Le serveur, lui, trie les 1 027 releves du
    //  puits — et, pour les trouver, lit les 62 500.
    // =================================================================

    public List<String> r4() {
        return triees(releves.findTop10ByPuitsOrderByAmplitudeDescMesureLeDesc(PUITS_PICS)
                .stream().map(DuneRequetes::ligneR4).toList());
    }

    static String ligneR4(Releve r) {
        return jjmmaaaa(r.mesureLe()) + "   amplitude " + d(deci(r.amplitude(), 3), 6);
    }

    // =================================================================
    //  Les quatre, mises en forme pour le banc.
    // =================================================================
    //  Le tri final porte sur les LIGNES, pas sur les documents : il rend
    //  la comparaison independante de l'ordre dans lequel le plan a sorti
    //  les documents. Le tri qui compte, lui, est dans la requete.

    public List<Requete> quatre() {
        return List.of(
                new Requete("R1", "Les collectes d un contremaitre",
                        this::r1,
                        () -> explications.find("collectes", filtreR1())),
                new Requete("R2", "Les echecs d un puits, du plus recent",
                        this::r2,
                        () -> explications.find("collectes", filtreR2(),
                                new org.bson.Document("debut", -1), null)),
                new Requete("R3", "Ce qu une region a sorti sur un mois",
                        this::r3,
                        () -> explications.aggregate("collectes",
                                pipelineR3().toPipeline(Aggregation.DEFAULT_CONTEXT))),
                new Requete("R4", "Les dix plus fortes secousses d un puits",
                        this::r4,
                        () -> explications.find("releves",
                                new org.bson.Document("puits", PUITS_PICS),
                                new org.bson.Document("amplitude", -1).append("mesureLe", -1), 10)));
    }

    static List<String> triees(List<String> lignes) {
        return lignes.stream().sorted(Comparator.naturalOrder()).toList();
    }

    static double nombre(org.bson.Document r, String champ) {
        Object v = r.get(champ);
        return v == null ? 0 : ((Number) v).doubleValue();
    }
}
