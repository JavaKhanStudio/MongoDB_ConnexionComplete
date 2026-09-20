package fr.formation.connexion.commun;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.FindIterable;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@code explain()}, que Spring Data n'expose pas.
 *
 * <p>C'est une des trois choses pour lesquelles ce projet garde le
 * driver sous la main, sous le repository : {@code MongoRepository} sait
 * poser la question, pas demander au serveur COMMENT il compte y
 * repondre. Consequence a assumer : le filtre d'une requete est ecrit
 * deux fois — une fois en requete derivee, une fois en {@code Document}
 * pour l'explication. Les deux doivent dire la meme chose, et le banc le
 * verifie indirectement : si l'explication decrivait un autre filtre, la
 * colonne « plan » ne collerait pas aux colonnes « lus » et « cles ».
 *
 * <p>Les deux autres : {@code serverStatus} (voir {@link Banc}) et le
 * pipeline qui ECRIT — le {@code $merge} de la reference etendue, qu'on
 * passe au driver tel quel (voir les services {@code *Optimisation}).
 */
@Service
public class Explications {

    private final MongoTemplate mongo;

    public Explications(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Document find(String collection, Document filtre) {
        return find(collection, filtre, null, null);
    }

    /** La meme, avec une projection : de quoi montrer la requete couverte. */
    public Document findProjete(String collection, Document filtre, Document projection) {
        return mongo.getCollection(collection).find(filtre).projection(projection)
                .explain(ExplainVerbosity.EXECUTION_STATS);
    }

    public Document find(String collection, Document filtre, Document tri, Integer limite) {
        FindIterable<Document> it = mongo.getCollection(collection).find(filtre);
        if (tri != null) {
            it = it.sort(tri);
        }
        if (limite != null) {
            it = it.limit(limite);
        }
        return it.explain(ExplainVerbosity.EXECUTION_STATS);
    }

    public Document aggregate(String collection, List<Document> pipeline) {
        return mongo.getCollection(collection).aggregate(pipeline)
                .explain(ExplainVerbosity.EXECUTION_STATS);
    }
}
