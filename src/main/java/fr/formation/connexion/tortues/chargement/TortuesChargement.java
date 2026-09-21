package fr.formation.connexion.tortues.chargement;

import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Chargement;
import fr.formation.connexion.commun.Tirage;
import fr.formation.connexion.commun.Tirage.Poids;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.g;
import static fr.formation.connexion.commun.Formats.moyenne;
import static fr.formation.connexion.commun.Formats.n;
import static fr.formation.connexion.commun.Tirage.nombre;
import static fr.formation.connexion.tortues.service.TortuesRequetes.ANNEE_DEBUT;
import static fr.formation.connexion.tortues.service.TortuesRequetes.ANNEE_FIN;
import static fr.formation.connexion.tortues.service.TortuesRequetes.MOIS_DEBUT;
import static fr.formation.connexion.tortues.service.TortuesRequetes.MOIS_FIN;
import static fr.formation.connexion.tortues.service.TortuesRequetes.OBSERVATEUR;
import static fr.formation.connexion.tortues.service.TortuesRequetes.SITE;
import static fr.formation.connexion.tortues.service.TortuesRequetes.STATUT_UICN;
import static fr.formation.connexion.tortues.service.TortuesRequetes.TAG;

/**
 * DB 2 — « Tortues » : le chargement de la base NON OPTIMISEE.
 *
 * <p>Le portage de {@code sujets/2-tortues/charger.js}, tirage pour
 * tirage. L'espece est RECOPIEE dans la tortue (reference etendue deja
 * la, heritee de « SQL vers NoSQL ») ; l'observation, elle, ne connait
 * que le numero de la tortue — et rien de son espece.
 */
@Service
public class TortuesChargement extends Chargement {

    private static final Date DEBUT = utc(2019, 1, 1);
    private static final Date FIN = utc(2026, 12, 31);

    private static final Object[][] HABITATS = {
            {"Récif de Toliara", true}, {"Baie de Palawan", true}, {"Golfe du Mexique", false},
            {"Grande Barrière de corail", true}, {"Archipel des Chagos", true},
            {"Côte des Squelettes", true}, {"Mer d'Andaman", false}, {"Golfe de Guinée", false},
            {"Îles Galápagos", true}, {"Baie de Sepetiba", false}, {"Récif de Ningaloo", true},
            {"Côte de Guyane", true}, {"Mer de Sulu", false}, {"Delta de l'Orénoque", true}
    };

    private static final Object[][] SITES_CONNUS = {
            {"Lady Elliot", 3}, {"Cairns", 3}, {"Anakao", 0}, {"Toliara", 0}, {"Cabo Rojo", 2},
            {"Padre Island", 2}, {"El Nido", 1}, {"Heron Island", 3}, {"Tecolutla", 2},
            {"Exmouth", 10}, {"Coral Bay", 10}, {"São Tomé", 7}, {"Príncipe", 7},
            {"Awala-Yalimapo", 11}, {"Rémire", 11}, {"Peros Banhos", 4}
    };
    private static final String[] SITES_MORCEAUX = {"Pointe", "Anse", "Plage", "Banc", "Passe",
            "Baie", "Cap", "Ilot", "Lagon", "Barre", "Crique", "Motu"};
    private static final String[] SITES_SUFFIXES = {"Nord", "Sud", "Est", "Ouest", "Neuve", "Rouge",
            "Blanche", "aux Palmes", "des Nids", "du Large", "de Sable", "aux Coraux"};

    private record Espece(String nomScientifique, String nomCommun, String statutUicn) {
    }

    private static final List<Espece> ESPECES = List.of(
            new Espece("Chelonia mydas", "Tortue verte", "EN"),
            new Espece("Caretta caretta", "Tortue caouanne", "VU"),
            new Espece("Natator depressus", "Tortue a dos plat", "DD"),
            new Espece("Eretmochelys imbricata", "Tortue imbriquee", "CR"),
            new Espece("Dermochelys coriacea", "Tortue luth", "VU"),
            new Espece("Lepidochelys olivacea", "Tortue olivatre", "VU"));
    private static final List<Poids<Espece>> TIRAGE_ESPECE = List.of(
            Poids.de(ESPECES.get(0), 30), Poids.de(ESPECES.get(1), 24), Poids.de(ESPECES.get(2), 12),
            Poids.de(ESPECES.get(3), 8), Poids.de(ESPECES.get(4), 14), Poids.de(ESPECES.get(5), 12));

    private static final String[][] ORGANISATIONS = {{"CNRS", "France"},
            {"Ministère de la Transition écologique", "France"}, {"AIMS", "Australie"},
            {"WWF México", "Mexique"}, {"IUCN", "Thaïlande"}, {"Ifremer", "France"},
            {"Charles Darwin Foundation", "Équateur"}};

    // nom, acronyme, organisation, annee de fin, statut, espece ciblee
    private static final Object[][] PROGRAMMES = {
            {"Argos Océan Indien", "ARGOI", 0, null, "ACTIF", 0},
            {"Plan national tortue luth", "PNTL", 1, 2030, "ACTIF", 4},
            {"Reef Watch Queensland", "RWQ", 2, null, "ACTIF", 0},
            {"Golfo Azul", "GAZ", 3, null, "ACTIF", 1},
            {"Turtle Watch Andaman", "TWA", 4, 2027, "ACTIF", 3},
            {"Sea Turtle Genome Project", "STGP", 0, 2028, "ACTIF", null},
            {"Kélonia Réunion", "KEL", 5, null, "SUSPENDU", null},
            {"Galápagos Nesting Survey", "GNS", 6, 2024, "TERMINE", 0},
            {"Nesting Beach Watch", "NBW", 2, null, "ACTIF", null},
            {"Bycatch Observer Network", "BON", 4, 2029, "ACTIF", null},
            {"Sargasso Tracking", "SART", 0, null, "ACTIF", 4},
            {"Coral Triangle Turtles", "CTT", 2, 2031, "ACTIF", 3}
    };
    private static final Integer[] INTERVALLES = {14, 21, 30, 60, 90};
    private static final String[] MARQUAGES = {"Balise Argos SPOT-6", "Marquage PIT + Argos",
            "Marquage externe titane", "Marquage externe inconel", "Photo-identification",
            "Balise satellite Fastloc"};

    private static final String[] TAGS = {"balise-argos", "adulte", "juvénile", "pondeuse",
            "vétérane", "récif", "jamais-revue", "capture-accidentelle", "migration-longue",
            "génotypée", "nid-suivi", "réhabilitée", "relachée", "blessure-helice"};

    private static final String[] OBSERVATEURS_CONNUS = {"Aline Roy", "Marc Vidal", "Hery Rakoto",
            "Lucia Mendez", "Tom Baker", "Ana Cruz", "James Okoro", "Ines Duarte", "Naina Andria",
            "Sophie Lambert"};
    private static final String[] PRENOMS = {"Awa", "Bruno", "Carla", "Diego", "Elena", "Farid",
            "Gabriela", "Hugo", "Iris", "Jonas", "Keiko", "Lena", "Malik", "Nina", "Omar", "Paula",
            "Quentin", "Rosa", "Samir", "Tania", "Ulysse", "Vera", "Wiam", "Yannick"};
    private static final String[] NOMS_FAMILLE = {"Moreau", "Silva", "Okafor", "Nakamura",
            "Ferreira", "Diallo", "Kowalski", "Romero", "Bennani", "Lindqvist", "Tavares", "Rasoa"};

    private static final String[] NOMS_TORTUE = {"Crush", "Goliath", "Squirt", "Kaia", "Nemo",
            "Ariel", "Bubbles", "Coco", "Dune", "Echo", "Flipper", "Gaia", "Haku", "Indigo", "Jade",
            "Koa", "Luna", "Mako", "Nori", "Opale", "Perle", "Quartz", "Reef", "Sable", "Tika",
            "Ulva", "Vaguelette", "Wave", "Xola", "Yara", "Zephyr", "Ambre", "Brise", "Corail",
            "Dorade", "Ecume"};

    public TortuesChargement(MongoTemplate mongo, Bases bases) {
        super(mongo, bases);
    }

    @Override
    public String nom() {
        return "tortues";
    }

    @Override
    protected void remplir(int volume, Tirage t) {
        final int nbTortues = 10000 * volume;
        final int nbObservations = 90000 * volume;

        // =============================================================
        //  1. Le referentiel
        // =============================================================
        List<Document> habitats = new ArrayList<>();
        for (Object[] h : HABITATS) {
            habitats.add(new Document("_id", h[0]).append("aireProtegee", h[1]));
        }
        List<Document> sites = new ArrayList<>();
        for (Object[] s : SITES_CONNUS) {
            sites.add(new Document("_id", s[0]).append("habitat", HABITATS[(Integer) s[1]][0]));
        }
        Set<String> vus = new HashSet<>();
        sites.forEach(s -> vus.add(s.getString("_id")));
        for (String m : SITES_MORCEAUX) {
            for (String s : SITES_SUFFIXES) {
                if (sites.size() >= 60) {
                    break;
                }
                String nomSite = m + " " + s;
                if (!vus.add(nomSite)) {
                    continue;
                }
                sites.add(new Document("_id", nomSite).append("habitat", t.choix(habitats).get("_id")));
            }
        }

        List<Document> programmes = new ArrayList<>();
        for (Object[] p : PROGRAMMES) {
            String[] org = ORGANISATIONS[(Integer) p[2]];
            Document doc = new Document("_id", p[1])
                    .append("nom", p[0])
                    .append("statut", p[4])
                    .append("organisation", new Document("nom", org[0]).append("pays", org[1]))
                    .append("protocole", new Document("intervalleJours", t.choix(INTERVALLES))
                            .append("methodeMarquage", t.choix(MARQUAGES)));
            if (p[3] != null) {
                doc.append("anneeFin", p[3]);
            }
            if (p[5] != null) {
                doc.append("especeCible", ESPECES.get((Integer) p[5]).nomScientifique());
            }
            programmes.add(doc);
        }

        List<String> observateurs = new ArrayList<>(List.of(OBSERVATEURS_CONNUS));
        Set<String> vusObs = new HashSet<>(observateurs);
        for (String p : PRENOMS) {
            for (String f : NOMS_FAMILLE) {
                if (observateurs.size() >= 120) {
                    break;
                }
                String nomObs = p + " " + f;
                if (!vusObs.add(nomObs)) {
                    continue;
                }
                observateurs.add(nomObs);
            }
        }

        titre("Le referentiel");
        verser("habitats", habitats);
        dire(g("habitats", 16) + d(habitats.size(), 8));
        verser("sites", sites);
        dire(g("sites", 16) + d(sites.size(), 8));
        verser("programmes", programmes);
        dire(g("programmes", 16) + d(programmes.size(), 8));
        verser("especes", ESPECES.stream().map(e -> new Document("_id", e.nomScientifique())
                .append("nomCommun", e.nomCommun()).append("statutUicn", e.statutUicn())).toList());
        dire(g("especes", 16) + d(ESPECES.size(), 8));

        // =============================================================
        //  2. Les tortues — l'espece y est RECOPIEE
        // =============================================================
        String[] statutDeTortue = new String[nbTortues + 1];
        List<String> attR4 = new ArrayList<>();

        titre("Les tortues");
        int id = 0;
        while (id < nbTortues) {
            List<Document> lot = new ArrayList<>();
            int jusqua = Math.min(id + LOT, nbTortues);
            while (id < jusqua) {
                id++;
                Espece e = t.pondere(TIRAGE_ESPECE);
                List<String> tags = new ArrayList<>();
                for (int k = t.entier(0, 4); k > 0; k--) {
                    String lib = t.choix(TAGS);
                    if (!tags.contains(lib)) {
                        tags.add(lib);
                    }
                }
                List<Document> insc = new ArrayList<>();
                for (int k = t.entier(0, 2); k > 0; k--) {
                    Document p = t.choix(programmes);
                    if (insc.stream().noneMatch(i -> i.get("acronyme").equals(p.get("_id")))) {
                        insc.add(new Document("acronyme", p.get("_id"))
                                .append("dateInscription", t.date(DEBUT, FIN)));
                    }
                }
                Document doc = new Document("_id", id)
                        .append("nom", t.choix(NOMS_TORTUE) + " " + d(id, 5))
                        .append("espece", new Document("nomScientifique", e.nomScientifique())
                                .append("nomCommun", e.nomCommun())
                                .append("statutUicn", e.statutUicn()))
                        .append("mensurations", new Document(
                                "longueurDossiereCm", nombre(t.arrondi(38, 165, 1)))
                                .append("poidsKg", nombre(t.arrondi(9, 420, 1))))
                        .append("tags", tags)
                        .append("programmes", insc);
                if (!t.chance(1.0 / 30)) {
                    Document h = t.choix(habitats);
                    doc.append("habitat", new Document("nom", h.get("_id"))
                            .append("aireProtegee", h.get("aireProtegee")));
                }
                statutDeTortue[id] = e.statutUicn();
                lot.add(doc);
                if (tags.contains(TAG)) {
                    attR4.add(ligneR4(doc));
                }
            }
            lot("tortues", lot);
            if (id % 20000 == 0) {
                dire(d(n(id), 9) + " tortues");
            }
        }
        dire(d(n(nbTortues), 9) + " tortues en tout");

        // =============================================================
        //  3. Les observations — elles ne connaissent que le numero
        // =============================================================
        Date anneeDebut = Date.from(ANNEE_DEBUT);
        Date anneeFin = Date.from(ANNEE_FIN);
        Date moisDebut = Date.from(MOIS_DEBUT);
        Date moisFin = Date.from(MOIS_FIN);
        List<String> attR1 = new ArrayList<>();
        List<String> attR2 = new ArrayList<>();
        Map<String, long[]> attR3 = new LinkedHashMap<>();    // site -> { somme, n }

        titre("Les observations");
        id = 0;
        while (id < nbObservations) {
            List<Document> lot = new ArrayList<>();
            int jusqua = Math.min(id + LOT, nbObservations);
            while (id < jusqua) {
                id++;
                int tortue = t.entier(1, nbTortues);          // l'observation connait la tortue ...
                Document o = new Document("_id", id)
                        .append("tortue", tortue)
                        .append("site", t.choix(sites).get("_id"))
                        .append("observateur", t.choix(observateurs))
                        .append("date", t.date(DEBUT, FIN))
                        .append("scoreSante", t.entier(1, 10));  // ... mais rien de son espece.
                lot.add(o);

                Date date = o.getDate("date");
                if (OBSERVATEUR.equals(o.getString("observateur"))) {
                    attR1.add(ligneR1(o));
                }
                if (SITE.equals(o.get("site")) && !date.before(anneeDebut) && date.before(anneeFin)) {
                    attR2.add(ligneR2(o));
                }
                if (!date.before(moisDebut) && date.before(moisFin)
                        && STATUT_UICN.equals(statutDeTortue[tortue])) {
                    long[] a = attR3.computeIfAbsent(o.getString("site"), k -> new long[2]);
                    a[0] += o.getInteger("scoreSante");
                    a[1]++;
                }
            }
            lot("observations", lot);
            if (id % 120000 == 0) {
                dire(d(n(id), 9) + " observations");
            }
        }
        dire(d(n(nbObservations), 9) + " observations en tout");

        // =============================================================
        //  4. La reponse attendue des quatre requetes
        // =============================================================
        List<String> r3 = new ArrayList<>();
        attR3.forEach((site, a) -> r3.add(ligneR3(site, a[0], a[1])));
        attR1.sort(null);
        attR2.sort(null);
        r3.sort(null);
        attR4.sort(null);
        reference(
                attendu("R1", "Les observations d un observateur", attR1),
                attendu("R2", "Les observations d un site sur une annee", attR2),
                attendu("R3", "Score moyen des especes en danger critique", r3),
                attendu("R4", "Les tortues portant un tag", attR4));

        bilan(16, "habitats", "sites", "especes", "programmes", "tortues", "observations");
    }

    // -----------------------------------------------------------------
    //  Les lignes de la reponse attendue — ligneR1..R4 de requetes.js.
    // -----------------------------------------------------------------

    private static String ligneR1(Document o) {
        return d(o.get("_id"), 8) + "  " + jjmmaaaa(o.getDate("date")) + "  "
                + g(o.get("site"), 18) + d(o.get("scoreSante"), 3);
    }

    private static String ligneR2(Document o) {
        return jjmmaaaa(o.getDate("date")) + "  " + g(o.get("observateur"), 18)
                + " score " + d(o.get("scoreSante"), 3);
    }

    private static String ligneR3(String site, long somme, long nb) {
        return g(site, 18) + " moyenne " + moyenne(somme, nb, 2)
                + " sur " + d(nb, 4) + " observations";
    }

    private static String ligneR4(Document t) {
        Document habitat = t.get("habitat", Document.class);
        return d(t.get("_id"), 7) + "  " + g(t.get("nom"), 16)
                + g(t.get("espece", Document.class).get("nomCommun"), 20)
                + (habitat != null ? habitat.getString("nom") : "(sans habitat)");
    }
}
