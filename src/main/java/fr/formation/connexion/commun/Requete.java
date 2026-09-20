package fr.formation.connexion.commun;

import org.bson.Document;

import java.util.List;
import java.util.function.Supplier;

/**
 * Une des quatre requetes d'un sujet.
 *
 * <p>{@code jouer} rend des LIGNES deja mises en forme, pas des
 * documents : c'est ce qui permet de comparer la reponse d'une requete
 * reecrite a celle d'origine. La reponse doit rester la meme ; le
 * chemin, non.
 *
 * <p>{@code expliquer} rejoue la meme requete sans la jouer, pour lire
 * son plan. Elle passe par le driver plutot que par le repository :
 * Spring Data n'expose pas {@code explain()}, et c'est justement une des
 * choses que ce projet montre — on garde {@code MongoTemplate} et le
 * driver sous la main pour ce que le repository ne sait pas faire.
 */
public record Requete(
        String code,
        String intitule,
        Supplier<List<String>> jouer,
        Supplier<Document> expliquer) {
}
