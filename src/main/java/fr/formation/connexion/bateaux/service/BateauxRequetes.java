package fr.formation.connexion.bateaux.service;

import fr.formation.connexion.bateaux.model.Escale;
import fr.formation.connexion.bateaux.repo.EscaleRepository;
import fr.formation.connexion.commun.Etapes;
import fr.formation.connexion.commun.Explications;
import fr.formation.connexion.commun.Requete;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
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
 * DB 3 — Les bateaux : LES QUATRE REQUETES A OPTIMISER, en Spring Data.
 *
 * <p>Portage de {@code sujets/3-bateaux/requetes.js}. C'est le sujet ou
 * DEUX requetes sur quatre ne se reglent avec AUCUN index : R3 filtre
 * sur un champ que l'escale n'a pas, R4 trie sur un nombre qui n'est
 * ecrit nulle part.
 */
@Service
public class BateauxRequetes {

    /** Le Delta Amstel. */
    public static final String IMO = "IMO9500233";
    public static final String PORT = "Rotterdam";
    public static final Instant ANNEE_DEBUT = Instant.parse("2025-01-01T00:00:00Z");
    public static final Instant ANNEE_FIN = Instant.parse("2026-01-01T00:00:00Z");
    public static final String PAVILLON = "France";
    /** L'annee 2026. */
    public static final Instant REC_DEBUT = Instant.parse("2026-01-01T00:00:00Z");
    public static final Instant REC_FIN = Instant.parse("2027-01-01T00:00:00Z");

    private final EscaleRepository escales;
    private final MongoTemplate mongo;
    private final Explications explications;

    public BateauxRequetes(EscaleRepository escales, MongoTemplate mongo, Explications explications) {
        this.escales = escales;
        this.mongo = mongo;
        this.explications = explications;
    }

    // =================================================================
    //  R1 — « Le carnet de bord du Delta Amstel. »
    // =================================================================

    public List<String> r1() {
        return triees(escales.findByBateau(IMO).stream().map(BateauxRequetes::ligneR1).toList());
    }

    static String ligneR1(Escale e) {
        return d(e.id(), 8) + "  " + jjmmaaaa(e.arrivee()) + "  " + g(e.port(), 12)
                + "quai " + g(e.quai(), 5) + g(e.motif(), 12)
                + (e.depart() != null ? "parti le " + jjmmaaaa(e.depart()) : "ENCORE A QUAI");
    }

    // =================================================================
    //  R2 — « Les escales de Rotterdam en 2025, de la plus recente a la
    //  plus ancienne. »
    // =================================================================

    public List<String> r2() {
        return triees(escales.escalesDuPort(PORT, ANNEE_DEBUT, ANNEE_FIN).stream()
                .map(BateauxRequetes::ligneR2).toList());
    }

    static String ligneR2(Escale e) {
        return jjmmaaaa(e.arrivee()) + "  " + g(e.bateau(), 12) + "quai " + g(e.quai(), 5) + e.motif();
    }

    // =================================================================
    //  R3 — « Ce que les bateaux sous pavillon francais ont manipule en
    //  2026, port par port. »
    //  Le pavillon n'est PAS dans l'escale : il est dans le bateau. Il
    //  faut ouvrir le bateau de chaque escale de l'annee avant de
    //  pouvoir jeter celles qui ne sont pas francaises.
    // =================================================================

    /**
     * Le {@code $group} porte un {@code $sum} DANS un {@code $sum} :
     * le tonnage d'une escale est deja une somme (celle de ses
     * cargaisons), et on somme ces sommes-la par port. L'etage part en
     * BSON brut — voir {@link Etapes}.
     */
    public Aggregation pipelineR3() {
        return Aggregation.newAggregation(
                Etapes.brute(new Document("$match", new Document("arrivee",
                        new Document("$gte", Date.from(REC_DEBUT)).append("$lt", Date.from(REC_FIN))))),
                Etapes.brute(new Document("$lookup", new Document("from", "bateaux")
                        .append("localField", "bateau")
                        .append("foreignField", "_id")
                        .append("as", "b"))),
                Etapes.brute(new Document("$match", new Document("b.pavillon", PAVILLON))),
                Etapes.brute(new Document("$group", new Document("_id", "$port")
                        .append("tonnes", new Document("$sum",
                                new Document("$sum", "$cargaisons.tonnes")))
                        .append("n", new Document("$sum", 1)))));
    }

    public List<String> r3() {
        return triees(mongo.aggregate(pipelineR3(), "escales", Document.class)
                .getMappedResults().stream().map(BateauxRequetes::ligneR3).toList());
    }

    static String ligneR3(Document r) {
        return g(r.getString("_id"), 12) + d(deci(nombre(r, "tonnes"), 0), 12)
                + " t en " + d(r.get("n"), 5) + " escales";
    }

    // =================================================================
    //  R4 — « Les dix escales qui ont manipule le plus de tonnage. »
    //  Le tonnage d'une escale, c'est la somme de ses cargaisons. Il
    //  n'est ecrit nulle part : il se recalcule a chaque fois, pour les
    //  94 500 escales, avant de pouvoir en garder dix.
    // =================================================================

    public Aggregation pipelineR4() {
        return Aggregation.newAggregation(
                Etapes.brute(new Document("$set", new Document("total",
                        new Document("$sum", "$cargaisons.tonnes")))),
                Etapes.brute(new Document("$sort", new Document("total", -1).append("_id", 1))),
                Etapes.brute(new Document("$limit", 10)));
    }

    public List<String> r4() {
        return triees(mongo.aggregate(pipelineR4(), "escales", Document.class)
                .getMappedResults().stream().map(BateauxRequetes::ligneR4).toList());
    }

    static String ligneR4(Document e) {
        return d(e.get("_id"), 8) + "  " + g(e.getString("bateau"), 12) + g(e.getString("port"), 12)
                + d(deci(nombre(e, "total"), 0), 9) + " t";
    }

    // =================================================================
    //  Les quatre, mises en forme pour le banc.
    // =================================================================

    public List<Requete> quatre() {
        return List.of(
                new Requete("R1", "Le carnet de bord d un bateau",
                        this::r1,
                        () -> explications.find("escales", new Document("bateau", IMO))),
                new Requete("R2", "Les escales d un port sur une annee",
                        this::r2,
                        () -> explications.find("escales", filtreR2(),
                                new Document("arrivee", -1), null)),
                new Requete("R3", "Le tonnage d un pavillon, port par port",
                        this::r3,
                        () -> explications.aggregate("escales",
                                pipelineR3().toPipeline(Aggregation.DEFAULT_CONTEXT))),
                new Requete("R4", "Les dix plus grosses escales",
                        this::r4,
                        () -> explications.aggregate("escales",
                                pipelineR4().toPipeline(Aggregation.DEFAULT_CONTEXT))));
    }

    private Document filtreR2() {
        return new Document("port", PORT)
                .append("arrivee", new Document("$gte", Date.from(ANNEE_DEBUT))
                        .append("$lt", Date.from(ANNEE_FIN)));
    }

    static List<String> triees(List<String> lignes) {
        return lignes.stream().sorted(Comparator.naturalOrder()).toList();
    }

    static double nombre(Document r, String champ) {
        Object v = r.get(champ);
        return v == null ? 0 : ((Number) v).doubleValue();
    }
}
