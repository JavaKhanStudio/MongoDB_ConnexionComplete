package fr.formation.connexion.commun;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La reponse de reference — le juge.
 *
 * <p>Optimiser, c'est changer le chemin sans changer la reponse. Le
 * chargement ({@link Chargement}, portage de {@code charger.js}) a donc
 * enregistre, dans la collection {@code reference}, ce que chaque requete
 * repond sur la base fraiche — calcule a part, a partir des donnees
 * generees, PAS en rejouant les requetes du sujet. Cette collection est
 * celle du mongosh, au caractere pres : {@code make comparer} le verifie.
 *
 * <p>C'est cette collection-la que le Java interroge. Deux choses en
 * decoulent, et les deux comptent :
 * <ul>
 *   <li>un index ne change jamais une reponse ; une reference etendue
 *       mal recopiee, si. Le banc le voit tout de suite ;</li>
 *   <li>le portage Spring est juge par le MEME arbitre que le mongosh.
 *       Si les lignes Java ne sont pas caractere pour caractere celles
 *       du chargement, c'est que la requete Spring ne pose pas la meme
 *       question — et c'est un bug du portage, pas du serveur.</li>
 * </ul>
 */
@Service
public class Reference {

    private final MongoTemplate mongo;

    public Reference(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** {@code ok} vaut faux des qu'une seule ligne a bouge. */
    public record Verdict(boolean ok, List<String> lignes) {
    }

    @SuppressWarnings("unchecked")
    public Verdict verifier(List<Mesure> mesures) {
        Map<String, List<String>> attendu = new HashMap<>();
        for (Document r : mongo.getCollection("reference").find()) {
            attendu.put(r.getString("_id"), (List<String>) r.get("lignes"));
        }
        List<String> sortie = new ArrayList<>();
        if (attendu.isEmpty()) {
            sortie.add("  (pas de reponse de reference : recharger la base : make charger-" + mongo.getDb().getName() + ")");
            return new Verdict(true, sortie);
        }
        int ko = 0;
        for (Mesure m : mesures) {
            List<String> a = attendu.get(m.code());
            if (a == null) {
                continue;
            }
            List<String> o = m.lignes();
            if (a.equals(o)) {
                continue;
            }
            ko++;
            sortie.add("");
            sortie.add("  KO  " + m.code() + " ne rend plus la meme reponse qu au chargement :");
            for (int i = 0; i < Math.max(a.size(), o.size()); i++) {
                String la = i < a.size() ? a.get(i) : null;
                String lo = i < o.size() ? o.get(i) : null;
                if (java.util.Objects.equals(la, lo)) {
                    continue;
                }
                sortie.add("        au chargement |" + (la == null ? "  (pas de ligne)" : la));
                sortie.add("        maintenant    |" + (lo == null ? "  (pas de ligne)" : lo));
            }
        }
        if (ko == 0) {
            sortie.add("  Les " + mesures.size()
                    + " reponses sont celles du chargement : optimise, pas change.");
        } else {
            sortie.add("  " + ko + " requete(s) ne rendent plus la meme chose : ce n est plus la meme question.");
        }
        return new Verdict(ko == 0, sortie);
    }
}
