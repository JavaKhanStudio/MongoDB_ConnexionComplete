package fr.formation.connexion.commun;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.deci;
import static fr.formation.connexion.commun.Formats.g;
import static fr.formation.connexion.commun.Formats.n;

/**
 * LE BANC DE MESURE, porte de {@code sujets/outils.js}.
 *
 * <p>Ce qu'on veut montrer, c'est l'ecart entre ce que la requete REND
 * et ce que le serveur a du LIRE pour le rendre. {@code explain()} le
 * dit — mais pas au meme endroit selon la forme de la requete : un
 * {@code find} le met dans {@code executionStats}, un {@code aggregate}
 * l'eparpille entre l'etage {@code $cursor}, chaque {@code $lookup}, et
 * une somme au sommet. Lire le mauvais noeud fait mentir la mesure.
 *
 * <p>Les compteurs de {@code serverStatus().metrics.queryExecutor}, eux,
 * comptent TOUT ce que le moteur a touche, d'un seul nombre et sans
 * dependre du plan. Un seul client, une requete a la fois : la
 * difference entre avant et apres est exactement le prix de la requete.
 *
 * <p>Corollaire a dire en salle : ce banc suppose qu'on est SEUL sur le
 * serveur. Deux navigateurs qui rafraichissent en meme temps, et les
 * deux mesures se melangent.
 */
@Service
public class Banc {

    private final MongoTemplate mongo;

    public Banc(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Les deux compteurs du serveur, a l'instant present. */
    public record Compteurs(long lus, long cles) {
    }

    public Compteurs compteurs() {
        Document etat = mongo.executeCommand(new Document("serverStatus", 1));
        Document q = etat.get("metrics", Document.class).get("queryExecutor", Document.class);
        return new Compteurs(((Number) q.get("scannedObjects")).longValue(),
                             ((Number) q.get("scanned")).longValue());
    }

    /** Une requete, jouee pour de vrai, et ce qu'elle a coute. */
    public Mesure jouerUne(Requete r) {
        Compteurs avant = compteurs();
        long t0 = System.currentTimeMillis();
        List<String> lignes = r.jouer().get();
        long ms = System.currentTimeMillis() - t0;
        Compteurs apres = compteurs();
        String plan;
        try {
            plan = plan(r.expliquer().get());
        } catch (RuntimeException e) {
            plan = "explain KO";
        }
        return new Mesure(r.code(), r.intitule(), lignes.size(),
                apres.lus() - avant.lus(), apres.cles() - avant.cles(), ms, plan, lignes);
    }

    /**
     * Combien de documents le serveur a lus pendant qu'on faisait ca.
     * Sert aux explorations, ou ce qu'on compare n'est pas une des
     * quatre requetes du sujet mais deux facons d'ecrire la meme.
     */
    public long lus(Runnable f) {
        long avant = compteurs().lus();
        f.run();
        return compteurs().lus() - avant;
    }

    public List<Mesure> jouer(List<Requete> requetes) {
        List<Mesure> mesures = new ArrayList<>();
        for (Requete r : requetes) {
            mesures.add(jouerUne(r));
        }
        return mesures;
    }

    // -----------------------------------------------------------------
    //  Le plan, en deux mots
    // -----------------------------------------------------------------
    //  On ne lit pas l'arbre d'explain a l'ecran : on releve les etages
    //  qui coutent, et on les nomme.

    public static String plan(Document explication) {
        Set<String> vus = new LinkedHashSet<>();
        parcourir(explication, vus);
        List<String> bouts = new ArrayList<>();
        if (vus.contains("COLLSCAN")) {
            bouts.add("COLLSCAN");
        } else if (vus.contains("IXSCAN")) {
            bouts.add(vus.contains("FETCH") ? "IXSCAN" : "IXSCAN couvrant");
        } else if (vus.contains("DISTINCT_SCAN")) {
            bouts.add("DISTINCT_SCAN");
        } else if (vus.contains("EOF")) {
            bouts.add("EOF");
        }
        if (vus.contains("SORT")) {
            bouts.add("TRI");
        }
        if (vus.contains("LOOKUP")) {
            bouts.add("LOOKUP");
        }
        return bouts.isEmpty() ? "?" : String.join("+", bouts);
    }

    /**
     * L'etage de tete du plan RETENU, et de lui seul.
     *
     * <p>{@link #plan(Document)} parcourt tout l'arbre d'explain —
     * {@code rejectedPlans} compris. C'est ce que fait le mongosh du
     * projet, et ca ne gene pas le banc : un plan rejete porte les memes
     * etages que le gagnant, a peu de chose pres.
     *
     * <p>Mais pour la requete couverte, ca ment : le planificateur a
     * garde sous le coude un plan avec FETCH, et l'arbre entier repond
     * donc « IXSCAN » la ou le plan retenu dit
     * {@code PROJECTION_COVERED}. Ici on regarde le gagnant, et rien que
     * lui.
     */
    public static String planGagnant(Document explication) {
        Document queryPlanner = explication.get("queryPlanner", Document.class);
        if (queryPlanner == null) {
            return "?";
        }
        Document gagnant = queryPlanner.get("winningPlan", Document.class);
        if (gagnant == null) {
            return "?";
        }
        // Sur le moteur SBE, le plan classique est range un cran plus bas.
        Document classique = gagnant.get("queryPlan", Document.class);
        Document tete = classique != null ? classique : gagnant;
        return tete.getString("stage") != null ? tete.getString("stage") : "?";
    }

    private static void parcourir(Object x, Set<String> vus) {
        if (x instanceof List<?> liste) {
            liste.forEach(e -> parcourir(e, vus));
            return;
        }
        if (!(x instanceof Map<?, ?> doc)) {
            return;
        }
        if (doc.get("stage") instanceof String etage) {
            vus.add(etage);
        }
        for (Map.Entry<?, ?> e : doc.entrySet()) {
            if ("$lookup".equals(e.getKey())) {
                vus.add("LOOKUP");
            }
            parcourir(e.getValue(), vus);
        }
    }

    // -----------------------------------------------------------------
    //  Les tableaux, exactement ceux que mongosh imprime
    // -----------------------------------------------------------------
    //  La largeur des colonnes, une seule fois : l'en-tete et les lignes
    //  se fabriquent avec les memes appels, sinon elles finissent par
    //  glisser.

    private static final int L_CODE = 6, L_INTITULE = 42, L_RENDUS = 7,
            L_LUS = 11, L_CLES = 11, L_PLAN = 15, L_MS = 6;

    private static String ligneMesure(Object code, Object intitule, Object rendus,
                                      Object lus, Object cles, Object plan, Object ms) {
        String i = String.valueOf(intitule);
        return "  " + g(code, L_CODE) + g(i.substring(0, Math.min(i.length(), L_INTITULE - 1)), L_INTITULE)
                + d(rendus, L_RENDUS) + d(lus, L_LUS) + d(cles, L_CLES)
                + "  " + g(plan, L_PLAN) + d(ms, L_MS);
    }

    public static List<String> tableau(List<Mesure> mesures) {
        List<String> out = new ArrayList<>();
        out.add(ligneMesure("code", "ce qu elle demande", "rendus", "lus", "cles", "plan", "ms"));
        long lus = 0, rendus = 0, ms = 0;
        for (Mesure m : mesures) {
            out.add(ligneMesure(m.code(), m.intitule(), n(m.rendus()), n(m.lus()),
                    n(m.cles()), m.plan(), n(m.ms())));
            lus += m.lus();
            rendus += m.rendus();
            ms += m.ms();
        }
        out.add("");
        out.add("  " + n(lus) + " documents lus pour " + n(rendus) + " lignes rendues"
                + (rendus > 0 ? "  —  " + Math.round((double) lus / rendus) + " lus pour 1 rendu" : "")
                + ",  " + n(ms) + " ms en tout.");
        return out;
    }

    /** Le meme tableau, deux fois : avant, apres, et le facteur gagne. */
    public static List<String> compare(List<Mesure> avant, List<Mesure> apres) {
        List<String> out = new ArrayList<>();
        out.add(ligneCompare("code", "ce qu elle demande", "lus avant", "lus apres",
                "gain", "ms avant", "ms apres"));
        for (int i = 0; i < avant.size(); i++) {
            Mesure a = avant.get(i), b = apres.get(i);
            String gain;
            if (b.lus() == 0) {
                gain = a.lus() == 0 ? "—" : "infini";
            } else {
                double r = (double) a.lus() / b.lus();
                gain = (r >= 10 ? String.valueOf(Math.round(r)) : deci(r, 1)) + " x";
            }
            out.add(ligneCompare(a.code(), a.intitule(), n(a.lus()), n(b.lus()),
                    gain, n(a.ms()), n(b.ms())));
        }
        return out;
    }

    private static String ligneCompare(Object code, Object intitule, Object lusAvant,
                                       Object lusApres, Object gain, Object msAvant, Object msApres) {
        String i = String.valueOf(intitule);
        return "  " + g(code, L_CODE) + g(i.substring(0, Math.min(i.length(), L_INTITULE - 1)), L_INTITULE)
                + d(lusAvant, 12) + d(lusApres, 12) + d(gain, 9) + d(msAvant, 11) + d(msApres, 10);
    }
}
