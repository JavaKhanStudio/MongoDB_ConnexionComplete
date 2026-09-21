package fr.formation.connexion.dune.chargement;

import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Chargement;
import fr.formation.connexion.commun.Tirage;
import fr.formation.connexion.commun.Tirage.Poids;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.deci;
import static fr.formation.connexion.commun.Formats.g;
import static fr.formation.connexion.commun.Formats.n;
import static fr.formation.connexion.commun.Tirage.nombre;
import static fr.formation.connexion.dune.service.DuneRequetes.CONTREMAITRE;
import static fr.formation.connexion.dune.service.DuneRequetes.MOIS_DEBUT;
import static fr.formation.connexion.dune.service.DuneRequetes.MOIS_FIN;
import static fr.formation.connexion.dune.service.DuneRequetes.PUITS_ECHECS;
import static fr.formation.connexion.dune.service.DuneRequetes.PUITS_PICS;
import static fr.formation.connexion.dune.service.DuneRequetes.REGION;

/**
 * DB 1 — « Dune » : le chargement de la base NON OPTIMISEE.
 *
 * <p>Le portage de {@code sujets/1-dune/charger.js}, tirage pour tirage :
 * meme graine, meme base que le mongosh, au document pres. Le modele est
 * celui que « SQL vers NoSQL » faisait ecrire ; ce qui change, c'est qu'il
 * y a maintenant de quoi le sentir passer — et qu'il n'y a AUCUN index en
 * dehors de ceux des {@code _id}, et AUCUNE reference etendue.
 *
 * <p>Ce n'est pas un modele mal fait. C'est un modele qu'on n'a pas encore
 * regarde tourner.
 */
@Service
public class DuneChargement extends Chargement {

    private static final Date DEBUT = utc(2025, 1, 1);
    private static final Date FIN = utc(2026, 12, 31);

    // Les cinq regions de « SQL vers NoSQL », et sept autres : la carte
    // d'Arrakis s'est etendue, les noms qu'on connait sont toujours la.
    private static final Object[][] REGIONS = {
            {"Bassin de Tuono", "TUO", "NORD", 3.5}, {"Erg Habbanya", "HAB", "SUD", 8.2},
            {"Plaine Funeste", "FUN", "NORD", 6.0}, {"Depression de Tabr", "TAB", "SUD", 2.0},
            {"Muraille-Bouclier", "MUR", "NORD", 9.4}, {"Erg Cielago", "CIE", "SUD", 7.1},
            {"Bassin de Hagga", "HAG", "SUD", 5.5}, {"Passe du Vent", "VEN", "NORD", 8.8},
            {"Terre Brisee", "BRI", "NORD", 4.2}, {"Erg Mineur", "MIN", "SUD", 6.6},
            {"Gorge d Arrakeen", "ARR", "NORD", 1.8}, {"Grand Erg Central", "CEN", "SUD", 9.9}
    };

    private static final String[] VERS_CONNUS = {"Shai-Hulud le Vieux", "Gueule de Coriolis",
            "Le Silencieux", "Briseur de Dunes", "Fils du Sable", "Le Boiteux", "Ombre de Tabr"};
    private static final String[] VERS_DEBUT = {"Faiseur", "Devoreur", "Gueule", "Ombre", "Fils",
            "Briseur", "Cri"};
    private static final String[] VERS_FIN = {"des Sables", "de Coriolis", "du Nord", "des Dunes",
            "du Vide", "de l Erg", "des Tempetes", "du Bouclier"};
    private static final List<Poids<String>> STATUTS_VER = List.of(
            Poids.de("ACTIF", 5), Poids.de("DORMANT", 4), Poids.de("ABATTU", 1));

    private static final String[] PRENOMS = {"Gurney", "Rabban", "Stilgar", "Duncan", "Piter",
            "Chani", "Thufir", "Jessica", "Leto", "Alia", "Irulan", "Feyd", "Shaddam", "Wellington",
            "Liet", "Harah", "Jamis", "Otheym", "Korba", "Farok", "Shishakli", "Mapes", "Nefud",
            "Ramallo"};
    private static final String[] PATRONYMES = {"Halleck", "Harkonnen", "Idaho", "de Vries",
            "Atreides", "Corrino", "Fenring", "Hawat", "Kynes", "Mohiam", "Tuek", "Ecaz"};
    private static final String[] MAISONS = {"Atreides", "Harkonnen", "Fremen", "Guilde", "Corrino"};

    private static final String[] MODELES_MINAGE = {"Moissonneuse Harkonnen Mk IV",
            "Moissonneuse Harkonnen Mk V", "Moissonneuse Atreides Delta", "Foreuse legere Guilde",
            "Moissonneuse Ixienne T-3"};
    private static final String[] MODELES_TRANSPORT = {"Porteuse lourde Orni-9", "Porteuse Orni-6",
            "Aile de secours Guilde", "Porteuse Ixienne Kappa"};

    private static final List<Poids<String>> STATUTS = List.of(
            Poids.de("REUSSIE", 72), Poids.de("ECHOUEE", 28));
    private static final List<Poids<String>> CAUSES = List.of(
            Poids.de("VER", 35), Poids.de("TEMPETE", 30), Poids.de("PANNE", 25), Poids.de("EMBUSCADE", 10));
    private static final List<Poids<Integer>> PERTES = List.of(
            Poids.de(0, 50), Poids.de(1, 20), Poids.de(2, 12), Poids.de(3, 8), Poids.de(5, 5),
            Poids.de(8, 3), Poids.de(11, 2));

    /** Un puits, et sa richesse : elle sert au generateur, elle ne va pas en base. */
    private record Puits(String id, String region, double richesse) {
    }

    public DuneChargement(MongoTemplate mongo, Bases bases) {
        super(mongo, bases);
    }

    @Override
    public String nom() {
        return "dune";
    }

    @Override
    protected void remplir(int volume, Tirage t) {
        final int nbCollectes = 37500 * volume;
        final int nbReleves = 62500 * volume;

        // =============================================================
        //  1. Le referentiel — petit, stable, et cite partout
        // =============================================================
        List<Document> regions = new ArrayList<>();
        List<Document> puitsDocs = new ArrayList<>();
        List<Puits> puits = new ArrayList<>();
        Map<String, List<String>> versParRegion = new HashMap<>();
        int iVer = 0;
        for (Object[] r : REGIONS) {
            String nomRegion = (String) r[0];
            // Les vers : un 1:N BORNE (deux ou trois par region, et ce sera
            // tout). Il reste donc DANS la region — ce n'est pas lui qu'on
            // optimise. La borne est retiree a CHAQUE tour, comme dans le .js.
            List<Document> vers = new ArrayList<>();
            for (int k = 0; k < t.entier(2, 3); k++) {
                String nomVer = iVer < VERS_CONNUS.length ? VERS_CONNUS[iVer++]
                        : t.choix(VERS_DEBUT) + " " + t.choix(VERS_FIN);
                vers.add(new Document("nom", nomVer).append("statut", t.pondere(STATUTS_VER)));
            }
            regions.add(new Document("_id", nomRegion)
                    .append("hemisphere", r[2])
                    .append("indiceTempete", nombre((Double) r[3]))
                    .append("vers", vers));
            versParRegion.put(nomRegion, vers.stream().map(v -> v.getString("nom")).toList());

            // Les puits : cinq par region, soixante en tout. Une collection
            // a eux, parce que 37 500 collectes les citent et qu'un puits
            // s'epuise.
            for (int k = 1; k <= 5; k++) {
                String id = r[1] + "-0" + k;
                Document p = new Document("_id", id)
                        .append("region", nomRegion)        // le puits sait ou il est ...
                        .append("epuise", t.chance(0.15))
                        .append("profondeurM", t.entier(40, 320));
                puits.add(new Puits(id, nomRegion, t.arrondi(0.4, 1.6, 2)));
                puitsDocs.add(p);
            }
        }

        // Les six contremaitres de « SQL vers NoSQL » restent en tete, avec
        // leur maison et leur anciennete. Les autres sont tires — il en faut
        // 240 pour qu'une egalite sur le nom soit une VRAIE egalite selective.
        List<Document> contremaitres = new ArrayList<>(List.of(
                contremaitre("Gurney Halleck", "Atreides", 22),
                contremaitre("Rabban Harkonnen", "Harkonnen", 15),
                contremaitre("Stilgar", "Fremen", 30),
                contremaitre("Duncan Idaho", "Atreides", 9),
                contremaitre("Piter de Vries", "Harkonnen", 6),
                contremaitre("Chani", "Fremen", 4)));
        Set<String> dejaVus = new HashSet<>();
        contremaitres.forEach(c -> dejaVus.add(c.getString("_id")));
        for (String p : PRENOMS) {
            for (String q : PATRONYMES) {
                if (contremaitres.size() >= 240) {
                    break;
                }
                String nomCm = p + " " + q;
                if (!dejaVus.add(nomCm)) {
                    continue;
                }
                contremaitres.add(contremaitre(nomCm, t.choix(MAISONS), t.entier(1, 34)));
            }
        }

        List<Document> transports = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            transports.add(new Document("_id", "PT-" + d(1000 + k * 27, 4))
                    .append("modele", t.choix(MODELES_TRANSPORT)));
        }
        List<Document> minages = new ArrayList<>();
        for (int k = 0; k < 60; k++) {
            minages.add(new Document("_id", "PM-" + d(400 + k * 19, 4))
                    .append("modele", t.choix(MODELES_MINAGE))
                    // compatibilite etait une table de liaison PURE : elle
                    // tient dans un tableau de matricules.
                    .append("transports", List.of(t.choix(transports).get("_id"),
                            t.choix(transports).get("_id"))));
        }

        titre("Le referentiel");
        verser("regions", regions);
        dire(g("regions", 22) + d(regions.size(), 8));
        verser("puits", puitsDocs);
        dire(g("puits", 22) + d(puitsDocs.size(), 8));
        verser("contremaitres", contremaitres);
        dire(g("contremaitres", 22) + d(contremaitres.size(), 8));
        verser("plateformesMinage", minages);
        dire(g("plateformesMinage", 22) + d(minages.size(), 8));
        verser("plateformesTransport", transports);
        dire(g("plateformesTransport", 22) + d(transports.size(), 8));

        // =============================================================
        //  2. Les collectes — le gros morceau, et le document polymorphe
        // =============================================================
        //  Fabriquees par paquets, et jamais gardees en entier : la
        //  reponse attendue des quatre requetes s'accumule au passage.
        //  Elle est calculee a partir des donnees, pas en rejouant les
        //  requetes : un portage qui se trompe de question est compare a
        //  la verite, pas a lui-meme.
        Date moisDebut = Date.from(MOIS_DEBUT);
        Date moisFin = Date.from(MOIS_FIN);
        List<String> attR1 = new ArrayList<>();
        List<String> attR2 = new ArrayList<>();
        Map<String, double[]> attR3 = new LinkedHashMap<>();   // puits -> { tonnes, n }

        titre("Les collectes");
        int id = 0;
        while (id < nbCollectes) {
            List<Document> lot = new ArrayList<>();
            int jusqua = Math.min(id + LOT, nbCollectes);
            while (id < jusqua) {
                id++;
                Puits p = t.choix(puits);
                Document cm = t.choix(contremaitres);
                Document c = new Document("_id", id)
                        .append("debut", t.date(DEBUT, FIN))
                        .append("statut", t.pondere(STATUTS))
                        .append("puits", p.id())                        // ... la collecte, elle, ne sait pas
                        .append("contremaitre", cm.getString("_id"))    //     dans quelle region elle est.
                        .append("plateformes", new Document("minage", t.choix(minages).get("_id"))
                                .append("transport", t.choix(transports).get("_id")));
                if ("REUSSIE".equals(c.getString("statut"))) {
                    c.append("tonnes", nombre(t.arrondi(90 * p.richesse(), 320 * p.richesse(), 2)));
                    c.append("puretePct", nombre(t.arrondi(52, 99, 1)));
                    c.append("dureeMinutes", t.entier(120, 300));
                } else {
                    String cause = t.pondere(CAUSES);
                    c.append("cause", cause);
                    c.append("materielPerdu", t.chance(0.4));
                    c.append("pertesHumaines", t.pondere(PERTES));
                    if ("VER".equals(cause)) {
                        c.append("ver", new Document("nom", t.choix(versParRegion.get(p.region()))));
                    }
                }
                lot.add(c);

                // La reponse attendue, accumulee au vol.
                Date debut = c.getDate("debut");
                boolean dansLeMois = !debut.before(moisDebut) && debut.before(moisFin);
                if (CONTREMAITRE.equals(c.getString("contremaitre"))) {
                    attR1.add(ligneR1(c));
                }
                if (PUITS_ECHECS.equals(p.id()) && "ECHOUEE".equals(c.getString("statut")) && dansLeMois) {
                    attR2.add(ligneR2(c));
                }
                if ("REUSSIE".equals(c.getString("statut")) && REGION.equals(p.region()) && dansLeMois) {
                    double[] a = attR3.computeIfAbsent(p.id(), k -> new double[2]);
                    a[0] += num(c, "tonnes");
                    a[1]++;
                }
            }
            lot("collectes", lot);
            if (id % 40000 == 0) {
                dire(d(n(id), 9) + " collectes");
            }
        }
        dire(d(n(nbCollectes), 9) + " collectes en tout");

        // =============================================================
        //  3. Les releves de vibration — un par heure et par puits
        // =============================================================
        //  Le seul 1:N reellement non borne du sujet. Il etait deja dehors
        //  dans « SQL vers NoSQL » ; il y reste, et il grossit.
        Comparator<Document> plusFortes = Comparator
                .comparingDouble((Document r) -> num(r, "amplitude")).reversed()
                .thenComparing((Document r) -> r.getDate("mesureLe"), Comparator.reverseOrder());
        List<Document> attR4 = new ArrayList<>();
        id = 0;
        titre("Les releves de vibration");
        while (id < nbReleves) {
            List<Document> lot = new ArrayList<>();
            int jusqua = Math.min(id + LOT, nbReleves);
            while (id < jusqua) {
                id++;
                Puits p = t.choix(puits);
                Document r = new Document("_id", id)
                        .append("puits", p.id())
                        .append("mesureLe", t.date(DEBUT, FIN))
                        .append("amplitude", nombre(t.arrondi(0.2, 9.9, 3)));
                lot.add(r);
                if (PUITS_PICS.equals(p.id())) {
                    attR4.add(r);
                }
            }
            lot("releves", lot);
            if (id % 40000 == 0) {
                dire(d(n(id), 9) + " releves");
            }
            // Le top dix se garde au fil de l'eau : inutile de garder 1 027 releves.
            attR4.sort(plusFortes);
            attR4 = new ArrayList<>(attR4.subList(0, Math.min(12, attR4.size())));
        }
        dire(d(n(nbReleves), 9) + " releves en tout");

        // =============================================================
        //  4. La reponse attendue des quatre requetes
        // =============================================================
        List<String> r3 = new ArrayList<>();
        attR3.forEach((puitsId, a) -> r3.add(ligneR3(puitsId, a[0], (long) a[1])));
        List<String> r4 = new ArrayList<>(attR4.subList(0, Math.min(10, attR4.size())).stream()
                .map(DuneChargement::ligneR4).toList());
        attR1.sort(null);
        attR2.sort(null);
        r3.sort(null);
        r4.sort(null);
        reference(
                attendu("R1", "Les collectes d un contremaitre", attR1),
                attendu("R2", "Les echecs d un puits, du plus recent", attR2),
                attendu("R3", "Ce qu une region a sorti sur un mois", r3),
                attendu("R4", "Les dix plus fortes secousses d un puits", r4));

        // Un ex aequo entre le dixieme et le onzieme releve rendrait R4
        // indecidable : deux plans corrects ne sortiraient pas les memes dix
        // lignes. On le dit plutot que de le laisser mordre plus tard.
        if (attR4.size() > 10 && plusFortes.compare(attR4.get(9), attR4.get(10)) == 0) {
            dire("");
            dire("ATTENTION : ex aequo entre le 10e et le 11e releve de " + PUITS_PICS
                    + " — R4 n est pas decidable avec cette graine. Changer --graine.");
        }

        bilan(22, "regions", "puits", "contremaitres", "plateformesMinage",
                "plateformesTransport", "collectes", "releves");
    }

    private static Document contremaitre(String nom, String maison, int anciennete) {
        return new Document("_id", nom).append("maison", maison).append("ancienneteAnnees", anciennete);
    }

    // -----------------------------------------------------------------
    //  Les lignes de la reponse attendue — ligneR1..R4 de requetes.js.
    //  Les requetes Spring fabriquent les leurs de leur cote
    //  (DuneRequetes) ; les deux doivent tomber d'accord, au caractere.
    // -----------------------------------------------------------------

    private static String ligneR1(Document c) {
        return d(c.get("_id"), 7) + "  " + jjmmaaaa(c.getDate("debut")) + "  "
                + g(c.get("puits"), 9) + g(c.get("statut"), 9)
                + ("REUSSIE".equals(c.getString("statut"))
                ? d(deci(num(c, "tonnes"), 2), 8) + " t"
                : d(c.get("cause"), 10));
    }

    private static String ligneR2(Document c) {
        return jjmmaaaa(c.getDate("debut")) + "  " + g(c.get("cause"), 11)
                + d(c.get("pertesHumaines"), 3) + " morts"
                + (c.getBoolean("materielPerdu") ? ",  materiel perdu" : "");
    }

    private static String ligneR3(String puits, double tonnes, long n) {
        return g(puits, 9) + d(deci(tonnes, 2), 11) + " t en " + d(n, 4) + " collectes";
    }

    private static String ligneR4(Document r) {
        return jjmmaaaa(r.getDate("mesureLe")) + "   amplitude " + d(deci(num(r, "amplitude"), 3), 6);
    }
}
