package fr.formation.connexion.dune.service;

import fr.formation.connexion.commun.Banc;
import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Explications;
import fr.formation.connexion.commun.Requete;
import fr.formation.connexion.dune.repo.CollecteRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.n;
import static fr.formation.connexion.dune.service.DuneRequetes.CONTREMAITRE;
import static fr.formation.connexion.dune.service.DuneRequetes.MOIS_DEBUT;
import static fr.formation.connexion.dune.service.DuneRequetes.MOIS_FIN;
import static fr.formation.connexion.dune.service.DuneRequetes.REGION;

/**
 * DB 1 — « Dune » : LA CORRECTION, en Spring Data.
 *
 * <p>Portage de {@code sujets/1-dune/optimiser.js} (branche
 * {@code correction} du depot MongoDB_Optimisation).
 *
 * <p>TROIS DES QUATRE requetes ne changent pas d'un caractere : seul
 * leur chemin change. La quatrieme, R3, est REECRITE — et c'est la toute
 * la lecon de la reference etendue : on ne peut pas indexer un champ que
 * le document n'a pas.
 */
@Service
public class DuneOptimisation {

    private final MongoTemplate mongo;
    private final Bases bases;
    private final Banc banc;
    private final Explications explications;
    private final DuneRequetes requetes;
    private final CollecteRepository collectes;

    public DuneOptimisation(MongoTemplate mongo, Bases bases, Banc banc, Explications explications,
                            DuneRequetes requetes, CollecteRepository collectes) {
        this.mongo = mongo;
        this.bases = bases;
        this.banc = banc;
        this.explications = explications;
        this.requetes = requetes;
        this.collectes = collectes;
    }

    // =================================================================
    //  Ce qu'on pose, et pourquoi
    // =================================================================

    public List<String> poser() {
        List<String> notes = new ArrayList<>();

        notes.add("=== R1 — une egalite : un index sur un champ " + "=".repeat(27));
        // 169 lignes sur 37 500 documents. Le serveur n'a aucun moyen de
        // savoir ou elles sont : il les lit toutes. Un index sur
        // contremaitre, et il va droit aux 169.
        //
        // Pourquoi pas { contremaitre: 1, debut: 1 } ? Parce que R1 ne
        // trie pas et ne filtre pas sur la date. Une cle de plus, c'est
        // une cle de plus a tenir a jour a CHAQUE ecriture, pour rien.
        mongo.indexOps("collectes").ensureIndex(
                new Index().on("contremaitre", Sort.Direction.ASC).named("contremaitre"));
        notes.add("  collectes { contremaitre: 1 }");

        notes.add("");
        notes.add("=== R2 — une egalite, un intervalle, un tri : l ordre des cles " + "=".repeat(10));
        // La regle est E-S-R : d'abord les Egalites, puis le Sort, puis
        // les Range. Ici puits et statut sont des egalites ; debut est a
        // la fois l'intervalle ET le tri, et il vient donc en dernier.
        //
        // Mis dans l'autre sens — { debut: 1, puits: 1, statut: 1 } —
        // l'index serait bien utilise, mais il faudrait parcourir TOUT
        // l'intervalle de dates pour y trouver le puits. Et le tri
        // deviendrait bloquant : le serveur ramasse tout, puis trie,
        // avant de rendre la premiere ligne.
        //
        // La direction, elle, est indifferente tant qu'il n'y a QU'UNE
        // cle de tri : le serveur sait lire un index a l'envers.
        mongo.indexOps("collectes").ensureIndex(new Index()
                .on("puits", Sort.Direction.ASC)
                .on("statut", Sort.Direction.ASC)
                .on("debut", Sort.Direction.ASC)
                .named("puits-statut-debut"));
        notes.add("  collectes { puits: 1, statut: 1, debut: 1 }");

        notes.add("");
        notes.add("=== R3 — la reference etendue : on ne peut pas indexer ce qu on n a pas " + "=".repeat(1));
        // R3 demande les collectes D UNE REGION. Or la collecte ne sait
        // pas dans quelle region elle est : elle connait son puits, et
        // c'est le puits qui connait la region. Aucun index sur collectes
        // ne peut donc servir ce filtre — il n'y a rien a indexer.
        //
        // La seule sortie est de faire descendre la region DANS la
        // collecte. C'est une reference etendue : le puits reste la
        // collection qui fait autorite, et la collecte en garde une copie
        // du seul champ qu'elle interroge.
        //
        // LE PRIX. Une copie se paie le jour ou l'original change. Ici il
        // ne change jamais : un puits ne se deplace pas d'une region a
        // l'autre. Ce n'est pas un hasard — c'est le critere. On etend
        // une reference sur un champ stable ; sur un champ qui bouge, on
        // paie l'ecriture partout, et on accepte de voir les deux copies
        // diverger entre-temps.
        long t0 = System.currentTimeMillis();
        etendreLaReference();
        notes.add("  region recopiee dans les collectes en " + n(System.currentTimeMillis() - t0) + " ms");

        // Et maintenant seulement, l'index. Meme regle E-S-R : deux
        // egalites, puis l'intervalle de dates.
        mongo.indexOps("collectes").ensureIndex(new Index()
                .on("region", Sort.Direction.ASC)
                .on("statut", Sort.Direction.ASC)
                .on("debut", Sort.Direction.ASC)
                .named("region-statut-debut"));
        notes.add("  collectes { region: 1, statut: 1, debut: 1 }");
        bases.noterCopie("collectes", "region", "puits.region");

        notes.add("");
        notes.add("=== R4 — le tri bloquant : dix lignes, 62 500 documents tries " + "=".repeat(11));
        // Dix lignes en sortie, et pourtant le serveur lit les 62 500
        // releves, garde les 1 027 du puits, les trie en memoire, et
        // jette 1 017. L'etage TRI du plan, c'est ca : il ne peut rien
        // rendre avant d'avoir tout vu.
        //
        // Un index qui porte DEJA les releves du puits dans l'ordre des
        // amplitudes decroissantes supprime le tri : le serveur suit
        // l'index, prend les dix premieres cles, et s'arrete. Ici la
        // direction compte : il y a deux cles de tri, et elles doivent
        // aller dans le meme sens que l'index (ou toutes les deux dans
        // l'autre sens).
        mongo.indexOps("releves").ensureIndex(new Index()
                .on("puits", Sort.Direction.ASC)
                .on("amplitude", Sort.Direction.DESC)
                .on("mesureLe", Sort.Direction.DESC)
                .named("puits-amplitude-mesure"));
        notes.add("  releves { puits: 1, amplitude: -1, mesureLe: -1 }");

        return notes;
    }

    /**
     * L'ecriture se fait en UNE passe : le {@code $lookup} qu'on refusait
     * de payer a chaque lecture, on le paie une seule fois, et
     * {@code $merge} repose le resultat dans la collection d'ou il sort.
     *
     * <p>Ce pipeline-la part au driver tel quel, en {@code Document}. Un
     * pipeline qui ECRIT n'a rien a rendre : {@code toCollection()} le
     * joue et ne ramene aucun document, la ou {@code MongoTemplate}
     * voudrait mapper un resultat qui n'existe pas.
     */
    private void etendreLaReference() {
        mongo.getCollection("collectes").aggregate(List.of(
                new Document("$lookup", new Document("from", "puits")
                        .append("localField", "puits")
                        .append("foreignField", "_id")
                        .append("as", "p")),
                new Document("$set", new Document("region", new Document("$first", "$p.region"))),
                new Document("$unset", "p"),
                new Document("$merge", new Document("into", "collectes")
                        .append("on", "_id")
                        .append("whenMatched", "replace")
                        .append("whenNotMatched", "fail"))
        )).toCollection();
    }

    // =================================================================
    //  R3, reecrite — la seule des quatre dont le TEXTE change
    // =================================================================

    public Aggregation pipelineR3Optimise() {
        return Aggregation.newAggregation(
                Aggregation.match(Criteria.where("region").is(REGION)
                        .and("statut").is("REUSSIE")
                        .and("debut").gte(java.util.Date.from(MOIS_DEBUT))
                        .lt(java.util.Date.from(MOIS_FIN))),
                Aggregation.group("puits").sum("tonnes").as("tonnes").count().as("n"));
    }

    public List<String> r3Optimisee() {
        return DuneRequetes.triees(mongo.aggregate(pipelineR3Optimise(), "collectes", Document.class)
                .getMappedResults().stream().map(DuneRequetes::ligneR3).toList());
    }

    /** Les quatre, dont R3 reecrite. Les trois autres sont rendues a l'identique. */
    public List<Requete> quatreOptimisees() {
        return requetes.quatre().stream()
                .map(r -> !"R3".equals(r.code()) ? r : new Requete("R3", r.intitule(),
                        this::r3Optimisee,
                        () -> explications.aggregate("collectes",
                                pipelineR3Optimise().toPipeline(Aggregation.DEFAULT_CONTEXT))))
                .toList();
    }

    // =================================================================
    //  L'Exploration — la requete couverte
    // =================================================================
    //  « La date et le puits des 169 collectes de Gurney Halleck, sans
    //  lire un seul document. »
    //
    //  Avec { contremaitre: 1 }, le serveur trouve les 169 cles dans
    //  l'index puis va CHERCHER les 169 documents, parce que l'index ne
    //  contient pas debut ni puits : c'est l'etage FETCH. Un index qui
    //  porte les trois champs les contient tous — il n'y a plus rien a
    //  aller chercher.
    //
    //  Deux conditions, et la seconde est celle qu'on oublie : la
    //  projection ne doit demander QUE des champs de l'index. _id en
    //  fait partie sans qu'on l'ait demande — il faut donc l'exclure
    //  explicitement, sinon le serveur ouvre le document rien que pour
    //  lui. Et Spring Data ne sait pas le retirer tout seul : la
    //  projection derivee du type de retour le garde toujours.

    public List<String> exploration() {
        List<String> notes = new ArrayList<>();
        mongo.indexOps("collectes").ensureIndex(new Index()
                .on("contremaitre", Sort.Direction.ASC)
                .on("debut", Sort.Direction.ASC)
                .on("puits", Sort.Direction.ASC)
                .named("couvrant-contremaitre"));

        long sansProjection = banc.lus(() -> collectes.findByContremaitre(CONTREMAITRE));
        long avecId = banc.lus(() -> collectes.findProjectedByContremaitre(CONTREMAITRE));
        long couverte = banc.lus(() -> collectes.couverteParContremaitre(CONTREMAITRE));

        Document ex = explications.findProjete("collectes",
                new Document("contremaitre", CONTREMAITRE),
                new Document("_id", 0).append("debut", 1).append("puits", 1));

        notes.add("  findByContremaitre           " + d(n(sansProjection), 6) + " documents lus"
                + "   (le document entier)");
        notes.add("  findProjectedBy...           " + d(n(avecId), 6) + " documents lus"
                + "   (projection Spring, _id compris)");
        notes.add("  @Query fields _id:0          " + d(n(couverte), 6) + " documents lus"
                + "   -> " + Banc.planGagnant(ex));
        notes.add("");
        notes.add("  Ce que cette requete ne peut plus rendre : le tonnage. Il faudrait");
        notes.add("  une quatrieme cle dans l index, a tenir a jour a chaque ecriture");
        notes.add("  sur 37 500 documents.");
        notes.add("");
        notes.add("  L index couvrant est retire en sortant : il n appartient pas a la");
        notes.add("  correction, il ne sert qu a montrer ca.");
        mongo.indexOps("collectes").dropIndex("couvrant-contremaitre");
        return notes;
    }
}
