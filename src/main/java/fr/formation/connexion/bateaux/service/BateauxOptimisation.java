package fr.formation.connexion.bateaux.service;

import com.mongodb.client.model.Filters;
import fr.formation.connexion.bateaux.repo.EscaleRepository;
import fr.formation.connexion.commun.Banc;
import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Etapes;
import fr.formation.connexion.commun.Explications;
import fr.formation.connexion.commun.Requete;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static fr.formation.connexion.bateaux.service.BateauxRequetes.PAVILLON;
import static fr.formation.connexion.bateaux.service.BateauxRequetes.REC_DEBUT;
import static fr.formation.connexion.bateaux.service.BateauxRequetes.REC_FIN;
import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.deci;
import static fr.formation.connexion.commun.Formats.n;

/**
 * DB 3 — Les bateaux : LA CORRECTION, en Spring Data.
 *
 * <p>Portage de {@code sujets/3-bateaux/optimiser.js} (branche
 * {@code correction}). C'est le seul des trois sujets ou DEUX requetes
 * changent de texte : R3 parce que le champ filtre n'est pas dans le
 * document, R4 parce que le nombre trie n'existe pas.
 */
@Service
public class BateauxOptimisation {

    private static final String MOTIF_AVARIE = "AVARIE";

    private final MongoTemplate mongo;
    private final Bases bases;
    private final Explications explications;
    private final BateauxRequetes requetes;
    private final EscaleRepository escales;

    public BateauxOptimisation(MongoTemplate mongo, Bases bases, Explications explications,
                               BateauxRequetes requetes, EscaleRepository escales) {
        this.mongo = mongo;
        this.bases = bases;
        this.explications = explications;
        this.requetes = requetes;
        this.escales = escales;
    }

    public List<String> poser() {
        List<String> notes = new ArrayList<>();

        notes.add("=== R1 — une egalite : un index sur un champ " + "=".repeat(27));
        mongo.indexOps("escales").ensureIndex(
                new Index().on("bateau", Sort.Direction.ASC).named("bateau"));
        notes.add("  escales { bateau: 1 }");

        notes.add("");
        notes.add("=== R2 — egalite, intervalle, tri : E-S-R " + "=".repeat(30));
        // L'egalite sur le port d'abord, l'intervalle ensuite. arrivee
        // est a la fois l'intervalle et la cle de tri : l'index sert les
        // deux, et l'etage TRI disparait du plan.
        mongo.indexOps("escales").ensureIndex(new Index()
                .on("port", Sort.Direction.ASC)
                .on("arrivee", Sort.Direction.ASC)
                .named("port-arrivee"));
        notes.add("  escales { port: 1, arrivee: 1 }");

        notes.add("");
        notes.add("=== R3 — la reference etendue : le champ est dans l autre collection " + "=".repeat(3));
        // Le pavillon est dans le bateau, pas dans l'escale. Aucun index
        // sur escales ne peut servir ce filtre : il n'y a rien a
        // indexer. On fait donc descendre le pavillon DANS l'escale.
        //
        // Un bateau change-t-il de pavillon ? Oui, ca arrive — c'est un
        // champ moins stable que la region d'un puits. Ce qu'on achete
        // est donc plus cher : le jour d'un changement de pavillon, il
        // faut reecrire toutes les escales de ce bateau, ou accepter que
        // les anciennes portent l'ancien pavillon. Ici, c'est meme la
        // bonne reponse : une escale de 2023 a bien eu lieu sous le
        // pavillon de 2023.
        long t0 = System.currentTimeMillis();
        etendreLaReference();
        notes.add("  pavillon recopie dans les escales en " + n(System.currentTimeMillis() - t0) + " ms");
        mongo.indexOps("escales").ensureIndex(new Index()
                .on("pavillon", Sort.Direction.ASC)
                .on("arrivee", Sort.Direction.ASC)
                .named("pavillon-arrivee"));
        notes.add("  escales { pavillon: 1, arrivee: 1 }");
        bases.noterCopie("escales", "pavillon", "bateaux.pavillon");

        notes.add("");
        notes.add("=== R4 — le champ calcule : on n indexe pas une somme " + "=".repeat(18));
        // On n'indexe pas { $sum: "$cargaisons.tonnes" } : un index
        // porte des CHAMPS, pas des expressions. Le tonnage total
        // n'existe nulle part — alors on l'ecrit.
        //
        // Ce n'est pas une reference etendue : rien n'est copie d'une
        // autre collection. C'est un CHAMP CALCULE, et il a son propre
        // prix : il faut le recalculer a chaque fois qu'une cargaison
        // bouge. On l'accepte parce qu'une cargaison ne bouge plus une
        // fois l'escale finie, et que la question est posee tous les
        // jours.
        //
        // updateMany avec un PIPELINE en second argument : c'est la
        // seule forme qui permet a la mise a jour de lire le document
        // qu'elle modifie.
        long t1 = System.currentTimeMillis();
        long modifiees = mongo.getCollection("escales").updateMany(Filters.empty(),
                List.of(new Document("$set", new Document("tonnesTotal",
                        new Document("$sum", "$cargaisons.tonnes")))))
                .getModifiedCount();
        notes.add("  tonnesTotal ecrit sur " + n(modifiees) + " escales en "
                + n(System.currentTimeMillis() - t1) + " ms");
        mongo.indexOps("escales").ensureIndex(new Index()
                .on("tonnesTotal", Sort.Direction.DESC)
                .on("_id", Sort.Direction.ASC)
                .named("tonnesTotal"));
        notes.add("  escales { tonnesTotal: -1, _id: 1 }");
        bases.noterCopie("escales", "tonnesTotal", "somme de escales.cargaisons.tonnes");

        return notes;
    }

    private void etendreLaReference() {
        mongo.getCollection("escales").aggregate(List.of(
                new Document("$lookup", new Document("from", "bateaux")
                        .append("localField", "bateau")
                        .append("foreignField", "_id")
                        .append("as", "b")),
                new Document("$set", new Document("pavillon", new Document("$first", "$b.pavillon"))),
                new Document("$unset", "b"),
                new Document("$merge", new Document("into", "escales")
                        .append("on", "_id")
                        .append("whenMatched", "replace")
                        .append("whenNotMatched", "fail"))
        )).toCollection();
    }

    // =================================================================
    //  R3 et R4, reecrites — les deux dont le TEXTE change
    // =================================================================

    /**
     * Une fois {@code pavillon} et {@code tonnesTotal} ecrits, le
     * {@code $group} n'a plus de {@code $sum} imbrique : il somme un
     * champ. L'API typee de Spring Data suffit, et se lit mieux que le
     * BSON — le contraire du pipeline d'origine.
     */
    public Aggregation pipelineR3Optimise() {
        return Aggregation.newAggregation(
                Aggregation.match(Criteria.where("pavillon").is(PAVILLON)
                        .and("arrivee").gte(Date.from(REC_DEBUT)).lt(Date.from(REC_FIN))),
                Aggregation.group("port").sum("tonnesTotal").as("tonnes").count().as("n"));
    }

    /**
     * Le tri et la limite se servent directement dans l'index : dix cles
     * lues, dix documents ouverts, et on s'arrete. Plus de {@code $set}
     * sur 94 500 documents, plus de tri en memoire.
     */
    public Aggregation pipelineR4Optimise() {
        return Aggregation.newAggregation(
                Etapes.brute(new Document("$sort", new Document("tonnesTotal", -1).append("_id", 1))),
                Etapes.brute(new Document("$limit", 10)),
                Etapes.brute(new Document("$set", new Document("total", "$tonnesTotal"))));
    }

    public List<String> r3Optimisee() {
        return BateauxRequetes.triees(
                mongo.aggregate(pipelineR3Optimise(), "escales", Document.class)
                        .getMappedResults().stream().map(BateauxRequetes::ligneR3).toList());
    }

    public List<String> r4Optimisee() {
        return BateauxRequetes.triees(
                mongo.aggregate(pipelineR4Optimise(), "escales", Document.class)
                        .getMappedResults().stream().map(BateauxRequetes::ligneR4).toList());
    }

    public List<Requete> quatreOptimisees() {
        return requetes.quatre().stream().map(r -> switch (r.code()) {
            case "R3" -> new Requete("R3", r.intitule(), this::r3Optimisee,
                    () -> explications.aggregate("escales",
                            pipelineR3Optimise().toPipeline(Aggregation.DEFAULT_CONTEXT)));
            case "R4" -> new Requete("R4", r.intitule(), this::r4Optimisee,
                    () -> explications.aggregate("escales",
                            pipelineR4Optimise().toPipeline(Aggregation.DEFAULT_CONTEXT)));
            default -> r;
        }).toList();
    }

    // =================================================================
    //  L'Exploration — l'index qui n'indexe qu'une partie des documents
    // =================================================================
    //  « Les escales pour avarie, de la plus recente. » Elles sont 5 %
    //  des escales. Un index ordinaire sur { motif: 1, arrivee: 1 } sert
    //  la requete — et range aussi les 95 % dont personne ne demande
    //  jamais rien. Un index se paie a l'ecriture et en memoire :
    //  celui-la fait vingt fois la taille utile.
    //
    //  partialFilterExpression dit a MongoDB de n'indexer QUE les
    //  documents qui repondent a une condition. Le meme service, pour un
    //  vingtieme. Le piege : la requete doit contenir la condition du
    //  filtre, sinon le planificateur refuse l'index — il ne peut pas
    //  savoir que ce qu'il n'a pas indexe n'aurait rien rendu.
    //
    //  A savoir : partialFilterExpression n'accepte pas { $exists: false }.
    //  Les index partiels de ce projet portent tous sur une egalite.

    public List<String> exploration() {
        List<String> notes = new ArrayList<>();

        mongo.indexOps("escales").ensureIndex(new Index()
                .on("motif", Sort.Direction.ASC)
                .on("arrivee", Sort.Direction.ASC)
                .named("motif-arrivee"));
        long entier = tailleIndex("motif-arrivee");
        mongo.indexOps("escales").dropIndex("motif-arrivee");

        mongo.indexOps("escales").ensureIndex(new Index()
                .on("arrivee", Sort.Direction.ASC)
                .named("avaries")
                .partial(PartialIndexFilter.of(Criteria.where("motif").is(MOTIF_AVARIE))));
        long partiel = tailleIndex("avaries");

        Document ex = explications.find("escales", new Document("motif", MOTIF_AVARIE),
                new Document("arrivee", -1), null);

        notes.add("  escales pour avarie      " + d(n(escales.countByMotif(MOTIF_AVARIE)), 9)
                + " sur " + n(escales.count()));
        notes.add("  index ordinaire          " + d(n(entier), 9) + " octets");
        notes.add("  index partiel            " + d(n(partiel), 9) + " octets   ("
                + deci((double) entier / partiel, 1) + " fois plus petit)");
        notes.add("  le plan s en sert ?      " + Banc.plan(ex) + ", "
                + n(clesLues(ex)) + " cles lues");
        notes.add("");
        notes.add("  L index d exploration est retire en sortant : il n appartient pas a la");
        notes.add("  correction.");
        mongo.indexOps("escales").dropIndex("avaries");
        return notes;
    }

    /**
     * La taille d'un index, par {@code $collStats}. Le vieux
     * {@code collStats} en commande est deprecie depuis MongoDB 6.2 ;
     * l'etage d'agregation, lui, est la voie officielle.
     */
    private long tailleIndex(String nom) {
        Document stats = mongo.getCollection("escales").aggregate(List.of(
                new Document("$collStats", new Document("storageStats", new Document()))
        )).first();
        if (stats == null) {
            return 0;
        }
        Document tailles = stats.get("storageStats", Document.class).get("indexSizes", Document.class);
        Object v = tailles.get(nom);
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static long clesLues(Document explication) {
        Document stats = explication.get("executionStats", Document.class);
        return stats == null ? 0 : ((Number) stats.get("totalKeysExamined")).longValue();
    }
}
