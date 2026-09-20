package fr.formation.connexion.commun;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;

/**
 * Un etage d'agregation ecrit en BSON, tel quel.
 *
 * <p>L'API typee de Spring Data couvre les etages courants — et ce
 * projet s'en sert partout ou elle est plus lisible que le BSON. Mais
 * elle s'arrete quelque part : {@code { $sum: "$cargaisons.tonnes" }}
 * imbrique dans un autre {@code $sum}, par exemple, n'a pas d'ecriture
 * Java plus claire que lui-meme — et une traduction approximative
 * changerait la reponse sans le dire.
 *
 * <p>Quand c'est le cas, on donne l'etage a Spring tel quel. Ce n'est
 * pas contourner le framework : {@code AggregationOperation} est une
 * interface publique, faite exactement pour ca.
 */
public final class Etapes {

    private Etapes() {
    }

    public static AggregationOperation brute(Document etage) {
        return new AggregationOperation() {
            @Override
            public Document toDocument(AggregationOperationContext context) {
                return etage;
            }
        };
    }
}
