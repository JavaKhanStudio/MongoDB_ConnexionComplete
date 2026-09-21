package fr.formation.connexion.commun;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.InsertManyOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.g;
import static fr.formation.connexion.commun.Formats.n;

/**
 * Le chargement d'une base — {@code charger.js} du projet mongosh, en Java.
 *
 * <p>LES DONNEES NE SONT PAS UN DUMP. Elles se fabriquent a partir d'une
 * graine ({@link Tirage}) : meme graine, meme base, sur toutes les
 * machines — et la meme, au document pres, que celle que
 * {@code MongoDB_Optimisation} fabrique en mongosh. On peut donc en
 * demander dix fois plus ({@code --volume=10}) sans transporter dix fois
 * plus de fichier.
 *
 * <p>Le chargement ecrit aussi la collection {@code reference} : ce que
 * chaque requete du sujet DOIT repondre, accumule au vol pendant qu'on
 * fabrique les documents. Elle est calculee a partir des donnees, PAS en
 * rejouant les requetes — c'est ce qui en fait un juge ({@link Reference}).
 * Les lignes qu'elle range sont donc mises en forme ici, a part, avec les
 * memes {@link Formats} que les requetes : si les deux ne tombent pas
 * d'accord, c'est la requete qui ne pose pas la bonne question.
 *
 * <p>Et le chargement ne pose AUCUN index en dehors des {@code _id} : la
 * base part du point de depart, c'est tout l'exercice.
 */
public abstract class Chargement {

    /** Les documents partent par paquets de cette taille. */
    protected static final int LOT = 10000;

    protected final MongoTemplate mongo;
    private final Bases bases;
    private Consumer<String> sortie;

    protected Chargement(MongoTemplate mongo, Bases bases) {
        this.mongo = mongo;
        this.bases = bases;
    }

    /** Le nom de la base, qui est aussi celui du sujet. */
    public abstract String nom();

    /** Jette la base et la refait, a partir de la graine. */
    public final void charger(int volume, long graine, Consumer<String> sortie) {
        this.sortie = sortie;
        mongo.getDb().drop();
        remplir(volume, new Tirage(graine));
    }

    /** Fabrique et verse les documents du sujet, dans l'ordre du .js. */
    protected abstract void remplir(int volume, Tirage t);

    // -----------------------------------------------------------------
    //  Les outils du chargement
    // -----------------------------------------------------------------

    protected MongoCollection<Document> col(String nom) {
        return mongo.getDb().getCollection(nom);
    }

    /**
     * Inserer des dizaines de milliers de documents d'un coup fait tomber
     * le serveur sur la limite des 16 Mo d'une commande. Par paquets.
     */
    protected int verser(String collection, List<Document> docs) {
        for (int i = 0; i < docs.size(); i += 5000) {
            lot(collection, docs.subList(i, Math.min(i + 5000, docs.size())));
        }
        return docs.size();
    }

    protected void lot(String collection, List<Document> docs) {
        col(collection).insertMany(docs, new InsertManyOptions().ordered(false));
    }

    /** La reponse attendue des quatre requetes, rangee pour le juge. */
    protected void reference(Document... reponses) {
        col("reference").insertMany(List.of(reponses));
    }

    protected static Document attendu(String code, String intitule, List<String> lignes) {
        return new Document("_id", code).append("intitule", intitule).append("lignes", lignes);
    }

    /** Le bilan : combien de documents par collection, et aucun index. */
    protected void bilan(int largeur, String... collections) {
        titre("La base " + nom() + " est chargee — et n a aucun index");
        for (String c : collections) {
            dire(g(c, largeur) + d(n(col(c).countDocuments()), 10) + " documents");
        }
        dire("");
        List<String> poses = bases.poses();
        dire("index en place (hors _id) : " + (poses.isEmpty() ? "aucun" : poses.size()));
        dire("");
        dire("  la suite : make " + nom() + ", puis GET /api/" + nom() + "/mesurer");
        sortie.accept("");
    }

    protected void titre(String t) {
        sortie.accept("");
        sortie.accept("=== " + t + " " + "=".repeat(Math.max(0, 70 - t.length())));
    }

    protected void dire(String t) {
        sortie.accept("  " + t);
    }

    /** {@code new Date(Date.UTC(annee, mois - 1, jour))} — ici, le mois compte a partir de 1. */
    protected static Date utc(int annee, int mois, int jour) {
        return Date.from(Instant.parse("%04d-%02d-%02dT00:00:00Z".formatted(annee, mois, jour)));
    }

    /** Une date du .js, pour {@link Formats#jjmmaaaa(Instant)}. */
    protected static String jjmmaaaa(Date dt) {
        return Formats.jjmmaaaa(dt.toInstant());
    }

    /** Un nombre lu dans un document : {@link Tirage#nombre} a pu l'ecrire en int comme en double. */
    protected static double num(Document doc, String champ) {
        return ((Number) doc.get(champ)).doubleValue();
    }
}
