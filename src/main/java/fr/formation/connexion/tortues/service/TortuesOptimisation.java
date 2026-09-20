package fr.formation.connexion.tortues.service;

import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Explications;
import fr.formation.connexion.commun.Requete;
import fr.formation.connexion.tortues.repo.TortueRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.n;
import static fr.formation.connexion.tortues.service.TortuesRequetes.MOIS_DEBUT;
import static fr.formation.connexion.tortues.service.TortuesRequetes.MOIS_FIN;
import static fr.formation.connexion.tortues.service.TortuesRequetes.STATUT_UICN;

/**
 * DB 2 — Les tortues : LA CORRECTION, en Spring Data.
 *
 * <p>Portage de {@code sujets/2-tortues/optimiser.js} (branche
 * {@code correction}). Trois requetes sur quatre ne changent pas d'un
 * caractere ; seule R3 est reecrite.
 */
@Service
public class TortuesOptimisation {

    /** L'exploration part de 2024. */
    private static final Instant DEPUIS = Instant.parse("2024-01-01T00:00:00Z");
    private static final String ACRONYME = "ARGOI";

    private final MongoTemplate mongo;
    private final Bases bases;
    private final Explications explications;
    private final TortuesRequetes requetes;
    private final TortueRepository tortues;

    public TortuesOptimisation(MongoTemplate mongo, Bases bases, Explications explications,
                               TortuesRequetes requetes, TortueRepository tortues) {
        this.mongo = mongo;
        this.bases = bases;
        this.explications = explications;
        this.requetes = requetes;
        this.tortues = tortues;
    }

    public List<String> poser() {
        List<String> notes = new ArrayList<>();

        notes.add("=== R1 — la cle etrangere que SQL indexait toute seule " + "=".repeat(17));
        // En SQL, observateur aurait ete une cle etrangere, et la cle
        // etrangere porte un index — offert avec la contrainte, sans que
        // personne l'ait demande. Ici il n'y en a aucun : 754 lignes
        // trouvees en lisant les 90 000.
        mongo.indexOps("observations").ensureIndex(
                new Index().on("observateur", Sort.Direction.ASC).named("observateur"));
        notes.add("  observations { observateur: 1 }");

        notes.add("");
        notes.add("=== R2 — egalite puis intervalle, dans cet ordre " + "=".repeat(23));
        // E-S-R : l'egalite sur le site d'abord, l'intervalle de dates
        // ensuite. Et comme date est aussi la cle de tri, l'index rend
        // le tri gratuit par la meme occasion — l'etage TRI disparait.
        mongo.indexOps("observations").ensureIndex(new Index()
                .on("site", Sort.Direction.ASC)
                .on("date", Sort.Direction.ASC)
                .named("site-date"));
        notes.add("  observations { site: 1, date: 1 }");

        notes.add("");
        notes.add("=== R3 — la reference etendue, et le prix d une copie de copie " + "=".repeat(9));
        // R3 filtre sur le statut UICN. Une observation ne le connait
        // pas : elle connait sa tortue, la tortue connait son espece, et
        // c'est l'espece qui porte le statut. Il est DEJA recopie dans
        // la tortue depuis « SQL vers NoSQL » — la copie qu'on ajoute
        // ici est donc une copie de copie.
        //
        // Ce n'est pas une faute, c'est un compte a tenir : le jour ou
        // l'UICN reclasse une espece, il y a maintenant TROIS endroits a
        // reecrire — especes, tortues, observations — et ils ne le
        // seront pas au meme instant. On accepte ca parce qu'une
        // reclassification arrive tous les dix ans et que R3 est posee
        // tous les jours.
        long t0 = System.currentTimeMillis();
        etendreLaReference();
        notes.add("  statutUicn recopie dans les observations en "
                + n(System.currentTimeMillis() - t0) + " ms");

        mongo.indexOps("observations").ensureIndex(new Index()
                .on("statutUicn", Sort.Direction.ASC)
                .on("date", Sort.Direction.ASC)
                .named("statut-date"));
        notes.add("  observations { statutUicn: 1, date: 1 }");
        bases.noterCopie("observations", "statutUicn", "tortues.espece.statutUicn");

        notes.add("");
        notes.add("=== R4 — un index sur un tableau : multicle " + "=".repeat(28));
        // tags est un tableau. MongoDB indexe alors CHAQUE element
        // separement : une tortue a quatre tags produit quatre cles.
        // L'index s'appelle multicle, il se pose exactement comme un
        // autre, et il se paie a l'ecriture.
        mongo.indexOps("tortues").ensureIndex(
                new Index().on("tags", Sort.Direction.ASC).named("tags"));
        notes.add("  tortues { tags: 1 }  (multicle)");
        notes.add("");
        notes.add("  Et c'est la requete qui gagne le MOINS des quatre — sept fois, quand");
        notes.add("  les autres gagnent cent fois. Ce n'est pas un rate : 1 344 tortues sur");
        notes.add("  10 000 portent ce tag. Un index ne paie que ce qu'il ecarte, et");
        notes.add("  celui-ci n'ecarte que huit documents sur dix. Le chiffre qui decide");
        notes.add("  s'appelle la SELECTIVITE, et on le regarde AVANT de poser l'index.");

        return notes;
    }

    /**
     * En une passe, et {@code $merge} repose le resultat la d'ou il
     * sort. Le lookup qu'on refusait de payer a chaque lecture est paye
     * une seule fois.
     */
    private void etendreLaReference() {
        mongo.getCollection("observations").aggregate(List.of(
                new Document("$lookup", new Document("from", "tortues")
                        .append("localField", "tortue")
                        .append("foreignField", "_id")
                        .append("as", "t")),
                new Document("$set", new Document("statutUicn",
                        new Document("$first", "$t.espece.statutUicn"))),
                new Document("$unset", "t"),
                new Document("$merge", new Document("into", "observations")
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
                Aggregation.match(Criteria.where("statutUicn").is(STATUT_UICN)
                        .and("date").gte(Date.from(MOIS_DEBUT)).lt(Date.from(MOIS_FIN))),
                Aggregation.group("site").sum("scoreSante").as("somme").count().as("n"));
    }

    public List<String> r3Optimisee() {
        return TortuesRequetes.triees(
                mongo.aggregate(pipelineR3Optimise(), "observations", Document.class)
                        .getMappedResults().stream().map(TortuesRequetes::ligneR3).toList());
    }

    public List<Requete> quatreOptimisees() {
        return requetes.quatre().stream()
                .map(r -> !"R3".equals(r.code()) ? r : new Requete("R3", r.intitule(),
                        this::r3Optimisee,
                        () -> explications.aggregate("observations",
                                pipelineR3Optimise().toPipeline(Aggregation.DEFAULT_CONTEXT))))
                .toList();
    }

    // =================================================================
    //  L'Exploration — les tortues inscrites a ARGOI depuis 2024
    // =================================================================
    //  Le geste naturel :
    //      { "programmes.acronyme": "ARGOI",
    //        "programmes.dateInscription": { $gte: 2024-01-01 } }
    //  Il rend TROP de tortues. MongoDB applique chaque condition au
    //  tableau, pas a un MEME element : une tortue inscrite a ARGOI en
    //  2019 ET a PNTL en 2025 satisfait les deux, chacune par un element
    //  different. $elemMatch est ce qui exige que ce soit le meme.
    //
    //  Cote index : { "programmes.acronyme": 1, "programmes.dateInscription": 1 }
    //  porte deux chemins DU MEME tableau — c'est permis (ce que MongoDB
    //  refuse, ce sont deux tableaux paralleles). Sans $elemMatch, le
    //  serveur ne peut pas resserrer les bornes sur les deux cles a la
    //  fois ; avec, il le peut.

    public List<String> exploration() {
        List<String> notes = new ArrayList<>();
        mongo.indexOps("tortues").ensureIndex(new Index()
                .on("programmes.acronyme", Sort.Direction.ASC)
                .on("programmes.dateInscription", Sort.Direction.ASC)
                .named("programmes-acronyme-date"));

        long naif = tortues.compterNaif(ACRONYME, DEPUIS);
        long juste = tortues.compterAvecElemMatch(ACRONYME, DEPUIS);

        Document exN = explications.find("tortues", filtreNaif());
        Document exJ = explications.find("tortues", filtreElemMatch());

        notes.add("  sans $elemMatch  " + d(n(naif), 7) + " tortues"
                + "   (dont certaines inscrites a " + ACRONYME + " bien avant 2024)");
        notes.add("  avec $elemMatch  " + d(n(juste), 7) + " tortues");
        notes.add("  cles lues        " + d(n(clesLues(exN)), 7) + " sans, "
                + n(clesLues(exJ)) + " avec : $elemMatch resserre aussi les bornes de l index.");
        notes.add("");
        notes.add("  L index d exploration est retire en sortant : il n appartient pas a la");
        notes.add("  correction.");
        mongo.indexOps("tortues").dropIndex("programmes-acronyme-date");
        return notes;
    }

    private static Document filtreNaif() {
        return new Document("programmes.acronyme", ACRONYME)
                .append("programmes.dateInscription", new Document("$gte", Date.from(DEPUIS)));
    }

    private static Document filtreElemMatch() {
        return new Document("programmes", new Document("$elemMatch",
                new Document("acronyme", ACRONYME)
                        .append("dateInscription", new Document("$gte", Date.from(DEPUIS)))));
    }

    private static long clesLues(Document explication) {
        Document stats = explication.get("executionStats", Document.class);
        return stats == null ? 0 : ((Number) stats.get("totalKeysExamined")).longValue();
    }
}
