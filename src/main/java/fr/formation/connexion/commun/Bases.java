package fr.formation.connexion.commun;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.g;
import static fr.formation.connexion.commun.Formats.n;

/**
 * Ce qui se fait sur la base elle-meme, et qui ne depend d'aucun sujet :
 * lister les index, et tout remettre comme au chargement.
 *
 * <p>Deux collections sont hors jeu partout : {@code reference}, qui
 * porte la reponse attendue, et {@code optimisations}, qui porte la
 * liste de ce que la correction a recopie — sans elle,
 * {@link #remettre()} ne saurait pas quels champs effacer.
 */
@Service
public class Bases {

    /** Les collections de service : jamais touchees, jamais mesurees. */
    public static final List<String> HORS_JEU = List.of("reference", "optimisations");

    private final MongoTemplate mongo;

    public Bases(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Les index poses sur la base, {@code _id} mis a part. */
    public List<String> poses() {
        List<String> l = new ArrayList<>();
        for (String c : mongo.getCollectionNames().stream().sorted().toList()) {
            for (Document ix : mongo.getCollection(c).listIndexes()) {
                if ("_id_".equals(ix.getString("name"))) {
                    continue;
                }
                l.add((g(c, 22) + g(ix.getString("name"), 30)
                        + (ix.get("partialFilterExpression") != null ? "   partiel" : ""))
                        .replaceAll("\\s+$", ""));
            }
        }
        return l;
    }

    /**
     * La base redevient celle du chargement, SANS la recharger : les
     * index tombent, et les champs que la correction avait recopies
     * s'effacent.
     *
     * <p>A quoi ca sert : mesurer deux fois de suite dans les memes
     * conditions. Un index pose reste pose ; sans ce bouton, la seule
     * facon de revenir au point de depart serait de tout recharger.
     */
    public List<String> remettre() {
        List<String> sortie = new ArrayList<>();
        int tombes = 0;
        for (String c : mongo.getCollectionNames()) {
            if (HORS_JEU.contains(c)) {
                continue;
            }
            for (Document ix : mongo.getCollection(c).listIndexes()) {
                String nom = ix.getString("name");
                if ("_id_".equals(nom)) {
                    continue;
                }
                mongo.getCollection(c).dropIndex(nom);
                sortie.add("  index tombe     " + g(c, 22) + nom);
                tombes++;
            }
        }
        if (tombes == 0) {
            sortie.add("  aucun index a retirer.");
        }

        if (mongo.collectionExists("optimisations")) {
            for (Document o : mongo.getCollection("optimisations").find()) {
                String collection = o.getString("collection");
                String champ = o.getString("champ");
                long modifies = mongo.getCollection(collection)
                        .updateMany(Filters.empty(), Updates.unset(champ))
                        .getModifiedCount();
                sortie.add("  copie effacee   " + g(collection, 22) + g(champ, 20)
                        + d(n(modifies), 9) + " documents");
            }
            mongo.getCollection("optimisations").drop();
        }
        return sortie;
    }

    /** Retient qu'un champ a ete recopie, pour que {@link #remettre()} sache le defaire. */
    public void noterCopie(String collection, String champ, String vientDe) {
        mongo.getCollection("optimisations").insertOne(new Document()
                .append("collection", collection)
                .append("champ", champ)
                .append("vient_de", vientDe));
    }
}
