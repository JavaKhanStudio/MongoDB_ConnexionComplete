package fr.formation.connexion.bateaux.chargement;

import fr.formation.connexion.commun.Bases;
import fr.formation.connexion.commun.Chargement;
import fr.formation.connexion.commun.Tirage;
import fr.formation.connexion.commun.Tirage.Poids;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.formation.connexion.bateaux.service.BateauxRequetes.ANNEE_DEBUT;
import static fr.formation.connexion.bateaux.service.BateauxRequetes.ANNEE_FIN;
import static fr.formation.connexion.bateaux.service.BateauxRequetes.IMO;
import static fr.formation.connexion.bateaux.service.BateauxRequetes.PAVILLON;
import static fr.formation.connexion.bateaux.service.BateauxRequetes.PORT;
import static fr.formation.connexion.bateaux.service.BateauxRequetes.REC_DEBUT;
import static fr.formation.connexion.bateaux.service.BateauxRequetes.REC_FIN;
import static fr.formation.connexion.commun.Formats.d;
import static fr.formation.connexion.commun.Formats.deci;
import static fr.formation.connexion.commun.Formats.g;
import static fr.formation.connexion.commun.Formats.n;
import static fr.formation.connexion.commun.Tirage.nombre;

/**
 * DB 3 — « Bateaux » : le chargement de la base NON OPTIMISEE.
 *
 * <p>Le portage de {@code sujets/3-bateaux/charger.js}, tirage pour
 * tirage. L'escale connait l'IMO du bateau, et rien de son pavillon ; le
 * tonnage d'une escale n'est ecrit nulle part, il faut additionner ses
 * cargaisons.
 */
@Service
public class BateauxChargement extends Chargement {

    private static final Date DEBUT = utc(2023, 1, 1);
    private static final Date FIN = utc(2026, 12, 31);

    private static final Object[][] PORTS = {
            {"Le Havre", "France", 16.0}, {"Rotterdam", "Pays-Bas", 24.0},
            {"Singapour", "Singapour", 20.0}, {"Shanghai", "Chine", 17.5},
            {"Toulon", "France", 12.0}, {"Norfolk", "Etats-Unis", 14.0},
            {"Djibouti", "Djibouti", 18.0}, {"Brest", "France", 11.0},
            // les autres
            {"Anvers", "Belgique", 17.0}, {"Hambourg", "Allemagne", 15.1},
            {"Valence", "Espagne", 16.0}, {"Le Piree", "Grece", 18.0},
            {"Gioia Tauro", "Italie", 18.0}, {"Tanger Med", "Maroc", 18.0},
            {"Alger", "Algerie", 12.0}, {"Beyrouth", "Liban", 15.5},
            {"Colombo", "Sri Lanka", 18.0}, {"Busan", "Coree du Sud", 17.0},
            {"Yokohama", "Japon", 16.0}, {"Vancouver", "Canada", 15.5},
            {"Santos", "Bresil", 15.0}, {"Durban", "Afrique du Sud", 12.8},
            {"Oslo", "Norvege", 11.0}, {"Gdansk", "Pologne", 16.5}
    };
    private static final String[] QUAIS = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3",
            "D1", "D2"};

    private static final List<Poids<String>> PAVILLONS = List.of(
            Poids.de("France", 14), Poids.de("Pays-Bas", 12), Poids.de("Singapour", 12),
            Poids.de("Chine", 12), Poids.de("Etats-Unis", 10), Poids.de("Panama", 12),
            Poids.de("Liberia", 10), Poids.de("Malte", 6), Poids.de("Grece", 6),
            Poids.de("Japon", 6), Poids.de("Norvege", 6), Poids.de("Djibouti", 4));
    private static final List<Poids<String>> CATEGORIES = List.of(
            Poids.de("CIVIL", 82), Poids.de("MILITAIRE", 18));

    private static final String[] ARMATEURS = {"Compagnie Havraise", "Delta Maritime",
            "Straits Lines", "Yangtze Freight", "Ocean Cablier", "Nordic Bulk", "Levant Shipping",
            "Austral Carriers", "Hanseatic Lines", "Pacifique Fret"};
    private static final String[] MARINES = {"Marine nationale", "US Navy", "Marine de la RPC",
            "Koninklijke Marine", "Royal Navy", "Marina Militare"};
    private static final String[] TYPES_CIVILS = {"Porte-conteneurs", "Vraquier", "Petrolier",
            "Chimiquier", "Cablier", "Roulier", "Methanier"};
    private static final String[] CLASSES = {"Fregate Horizon", "Destroyer Arleigh Burke",
            "Corvette Gowind", "Patrouilleur Flamant", "Fregate FREMM", "Porte-helicopteres Mistral"};

    private static final Object[][] BATEAUX_CONNUS = {
            {"Etoile de Brest", "IMO9412345", "CIVIL", "France", 13.5, "Le Havre"},
            {"Kerguelen Trader", "IMO9455121", "CIVIL", "France", 10.2, "Le Havre"},
            {"Delta Amstel", "IMO9500233", "CIVIL", "Pays-Bas", 15.2, "Rotterdam"},
            {"Delta Maas", "IMO9500234", "CIVIL", "Pays-Bas", 16.0, "Rotterdam"},
            {"Straits Pioneer", "IMO9611002", "CIVIL", "Singapour", 14.5, "Singapour"},
            {"Straits Meridian", "IMO9611003", "CIVIL", "Singapour", 11.0, "Singapour"},
            {"Yangtze Star", "IMO9722441", "CIVIL", "Chine", 16.5, "Shanghai"},
            {"Yangtze Ember", "IMO9722442", "CIVIL", "Chine", 12.8, "Shanghai"},
            {"Petrel du Nord", "IMO9388771", "CIVIL", "Liberia", 14.0, null},
            {"Cablier Aurore", "IMO9199887", "CIVIL", "France", 8.0, "Brest"},
            {"Ouragan", "IMO4501001", "MILITAIRE", "France", 6.5, "Toulon"},
            {"Vigilante", "IMO4501002", "MILITAIRE", "France", 5.4, "Toulon"},
            {"Sillage", "IMO4501003", "MILITAIRE", "France", 4.2, "Brest"},
            {"Endeavour Point", "IMO4602001", "MILITAIRE", "Etats-Unis", 12.0, "Norfolk"},
            {"Chesapeake", "IMO4602002", "MILITAIRE", "Etats-Unis", 6.8, "Norfolk"},
            {"Long March Tide", "IMO4703001", "MILITAIRE", "Chine", 6.0, null},
            {"Zeeland", "IMO4804001", "MILITAIRE", "Pays-Bas", 5.6, null},
            {"Lame de Fond", "IMO4501004", "MILITAIRE", "France", 9.5, "Toulon"}
    };

    private static final String[] NOMS_A = {"Etoile", "Petrel", "Albatros", "Mistral", "Sirocco",
            "Aurore", "Corsaire", "Meridien", "Boreal", "Austral", "Cormoran", "Goeland",
            "Tramontane", "Alize", "Zephyr", "Ocean", "Vent", "Lame", "Ecume", "Sillage", "Maree",
            "Cap", "Phare", "Ancre"};
    private static final String[] NOMS_B = {"du Nord", "de Brest", "des Glenan", "d Ouessant",
            "du Ponant", "du Levant", "de Malte", "d Anvers", "du Cap", "des Sables", "de Mer",
            "d Argent", "de Fer", "du Large", "de Minuit", "d Orient"};

    private static final String[] BREVETS = {"CAPITAINE ILLIMITE", "CAPITAINE 3000",
            "OFFICIER DE MARINE", "CAPITAINE 500", "SECOND CAPITAINE"};
    private static final String[] PRENOMS = {"Helene", "Jan", "Wei", "Aisha", "Marc", "Sarah",
            "Lim", "Pieter", "Nadia", "Tomas", "Yuki", "Karim", "Elsa", "Diego", "Anna", "Olav",
            "Rania", "Bjorn", "Chiara", "Ousmane", "Ingrid", "Rafael", "Mei", "Lukas"};
    private static final String[] PATRONYMES = {"Mercier", "Verhoeven", "Chen", "Farah", "Ollivier",
            "Whitfield", "Boon Hock", "Bakker", "Lindgren", "Moretti", "Da Silva", "Nakagawa",
            "Haddad", "Novak", "Fernandez", "Keita"};

    private static final String[] MARCHANDISES = {"Conteneurs secs", "Conteneurs refrigeres",
            "Produits chimiques", "Machines-outils", "Cereales", "Minerai de fer", "Voitures", "Bois",
            "Ciment", "Gaz naturel liquefie", "Petrole brut", "Engrais"};
    private static final List<Poids<String>> MOTIFS = List.of(
            Poids.de("COMMERCE", 70), Poids.de("RELACHE", 12), Poids.de("AVITAILLEMENT", 10),
            Poids.de("AVARIE", 5), Poids.de("TECHNIQUE", 3));

    /** Une escale reduite a ce que R4 en garde : son tonnage total. */
    private record Grosse(int id, String bateau, String port, long total) {
    }

    public BateauxChargement(MongoTemplate mongo, Bases bases) {
        super(mongo, bases);
    }

    @Override
    public String nom() {
        return "bateaux";
    }

    @Override
    protected void remplir(int volume, Tirage t) {
        final int nbBateaux = 4000 * volume;
        final int nbCapitaines = 1500 * volume;
        final int nbEscales = 94500 * volume;

        // =============================================================
        //  1. Le referentiel — les ports, les bateaux
        // =============================================================
        List<Document> ports = new ArrayList<>();
        for (Object[] p : PORTS) {
            ports.add(new Document("_id", p[0])
                    .append("pays", p[1])
                    .append("tirantEauMaxM", nombre((Double) p[2]))
                    .append("quais", List.of(Arrays.copyOfRange(QUAIS, 0, t.entier(4, 6)))));
        }

        Map<String, String> pavillonDe = new HashMap<>();
        List<Document> bateaux = new ArrayList<>();
        List<String> imos = new ArrayList<>();

        for (Object[] b : BATEAUX_CONNUS) {
            Document doc = new Document("_id", b[1]).append("nom", b[0]).append("categorie", b[2])
                    .append("pavillon", b[3]).append("tirantEauM", nombre((Double) b[4]));
            if (b[5] != null) {
                doc.append("portAttache", b[5]);
            }
            poser(garnir(doc, t), bateaux, imos, pavillonDe);
        }
        int suite = 9000000;
        while (bateaux.size() < nbBateaux) {
            suite += t.entier(3, 29);
            Document doc = new Document("_id", "IMO" + suite)
                    .append("nom", t.choix(NOMS_A) + " " + t.choix(NOMS_B))
                    .append("categorie", t.pondere(CATEGORIES))
                    .append("pavillon", t.pondere(PAVILLONS))
                    .append("tirantEauM", nombre(t.arrondi(3.5, 22.0, 1)));
            if (!t.chance(0.08)) {
                doc.append("portAttache", t.choix(ports).get("_id"));
            }
            poser(garnir(doc, t), bateaux, imos, pavillonDe);
        }

        titre("Le referentiel");
        verser("ports", ports);
        dire(g("ports", 14) + d(n(ports.size()), 9));
        verser("bateaux", bateaux);
        dire(g("bateaux", 14) + d(n(bateaux.size()), 9));

        // =============================================================
        //  2. Les capitaines — et leurs commandements, un tableau borne
        // =============================================================
        List<Document> capitaines = new ArrayList<>();
        Set<String> nomsVus = new HashSet<>();
        int k = 0;
        while (capitaines.size() < nbCapitaines) {
            // Au-dela de 380 tirages, les noms se numerotent : les
            // combinaisons prenom + patronyme s'epuisent. Le ++k du .js
            // avance k DEUX fois par tour, et c'est la graine qui le veut.
            String nomCap = t.choix(PRENOMS) + " " + t.choix(PATRONYMES);
            if (k > 380) {
                nomCap += " " + d(++k, 4);
            }
            k++;
            if (!nomsVus.add(nomCap)) {
                continue;
            }
            List<Document> commandements = new ArrayList<>();
            for (int j = t.entier(1, 4); j > 0; j--) {
                String imo = t.choix(imos);
                Document co = new Document("bateau", imo).append("debut", t.date(DEBUT, FIN));
                if (!t.chance(0.25)) {
                    co.append("fin", new Date(co.getDate("debut").getTime()
                            + t.entier(90, 900) * 86400000L));
                }
                commandements.add(co);          // fin absente = affectation en cours
            }
            capitaines.add(new Document("_id", nomCap).append("brevet", t.choix(BREVETS))
                    .append("commandements", commandements));
        }
        verser("capitaines", capitaines);
        dire(g("capitaines", 14) + d(n(capitaines.size()), 9));

        // =============================================================
        //  3. Les escales — le gros morceau
        // =============================================================
        Date anneeDebut = Date.from(ANNEE_DEBUT);
        Date anneeFin = Date.from(ANNEE_FIN);
        Date recDebut = Date.from(REC_DEBUT);
        Date recFin = Date.from(REC_FIN);
        List<String> attR1 = new ArrayList<>();
        List<String> attR2 = new ArrayList<>();
        Map<String, long[]> attR3 = new LinkedHashMap<>();    // port -> { tonnes, n }
        Comparator<Grosse> plusGrosses = Comparator.comparingLong(Grosse::total).reversed()
                .thenComparingInt(Grosse::id);
        List<Grosse> attR4 = new ArrayList<>();

        titre("Les escales");
        int id = 0;
        while (id < nbEscales) {
            List<Document> lot = new ArrayList<>();
            int jusqua = Math.min(id + LOT, nbEscales);
            while (id < jusqua) {
                id++;
                Document p = t.choix(ports);
                List<Document> cargaisons = new ArrayList<>();
                Document e = new Document("_id", id)
                        .append("arrivee", t.date(DEBUT, FIN))
                        .append("motif", t.pondere(MOTIFS))
                        .append("bateau", t.choix(imos))        // l escale connait l imo ...
                        .append("port", p.get("_id"))           // ... et rien du pavillon du bateau.
                        .append("quai", t.choix(p.getList("quais", String.class)))
                        .append("cargaisons", cargaisons);
                if (!t.chance(0.02)) {
                    e.append("depart", new Date(e.getDate("arrivee").getTime()
                            + t.entier(6, 120) * 3600000L));
                }
                if ("COMMERCE".equals(e.getString("motif"))) {
                    for (int j = t.entier(1, 3); j > 0; j--) {
                        cargaisons.add(new Document("marchandise", t.choix(MARCHANDISES))
                                .append("tonnes", t.entier(400, 98000)));
                    }
                }
                lot.add(e);

                Date arrivee = e.getDate("arrivee");
                String bateau = e.getString("bateau");
                String port = e.getString("port");
                if (IMO.equals(bateau)) {
                    attR1.add(ligneR1(e));
                }
                if (PORT.equals(port) && !arrivee.before(anneeDebut) && arrivee.before(anneeFin)) {
                    attR2.add(ligneR2(e));
                }
                long total = 0;
                for (Document c : cargaisons) {
                    total += c.getInteger("tonnes");
                }
                if (!arrivee.before(recDebut) && arrivee.before(recFin)
                        && PAVILLON.equals(pavillonDe.get(bateau))) {
                    long[] a = attR3.computeIfAbsent(port, x -> new long[2]);
                    a[0] += total;
                    a[1]++;
                }
                attR4.add(new Grosse(id, bateau, port, total));
            }
            lot("escales", lot);
            if (id % 50000 == 0) {
                dire(d(n(id), 9) + " escales");
            }
            attR4.sort(plusGrosses);
            attR4 = new ArrayList<>(attR4.subList(0, Math.min(12, attR4.size())));
        }
        dire(d(n(nbEscales), 9) + " escales en tout");

        // =============================================================
        //  4. La reponse attendue des quatre requetes
        // =============================================================
        List<String> r3 = new ArrayList<>();
        attR3.forEach((port, a) -> r3.add(ligneR3(port, a[0], a[1])));
        List<String> r4 = new ArrayList<>(attR4.subList(0, Math.min(10, attR4.size())).stream()
                .map(BateauxChargement::ligneR4).toList());
        attR1.sort(null);
        attR2.sort(null);
        r3.sort(null);
        r4.sort(null);
        reference(
                attendu("R1", "Le carnet de bord d un bateau", attR1),
                attendu("R2", "Les escales d un port sur une annee", attR2),
                attendu("R3", "Le tonnage d un pavillon, port par port", r3),
                attendu("R4", "Les dix plus grosses escales", r4));

        if (attR4.size() > 10 && attR4.get(9).total() == attR4.get(10).total()) {
            dire("");
            dire("ATTENTION : ex aequo entre la 10e et la 11e escale — R4 n est pas decidable");
            dire("            avec cette graine. Changer --graine.");
        }

        bilan(14, "ports", "bateaux", "capitaines", "escales");
    }

    private static void poser(Document doc, List<Document> bateaux, List<String> imos,
                              Map<String, String> pavillonDe) {
        bateaux.add(doc);
        imos.add(doc.getString("_id"));
        pavillonDe.put(doc.getString("_id"), doc.getString("pavillon"));
    }

    /** Le document polymorphe : un civil et un militaire n'ont pas les memes champs. */
    private static Document garnir(Document doc, Tirage t) {
        if ("CIVIL".equals(doc.getString("categorie"))) {
            doc.append("armateur", t.choix(ARMATEURS));
            String type = t.choix(TYPES_CIVILS);
            doc.append("typeCivil", type);
            doc.append("portEnLourdT", t.entier(3000, 210000));
            if ("Porte-conteneurs".equals(type)) {
                doc.append("capaciteEvp", t.entier(800, 24000));
            }
        } else {
            doc.append("marine", t.choix(MARINES));
            doc.append("classe", t.choix(CLASSES));
            doc.append("equipage", t.entier(40, 1800));
            doc.append("propulsionNucleaire", t.chance(0.12));
        }
        return doc;
    }

    // -----------------------------------------------------------------
    //  Les lignes de la reponse attendue — ligneR1..R4 de requetes.js.
    // -----------------------------------------------------------------

    private static String ligneR1(Document e) {
        Date depart = e.getDate("depart");
        return d(e.get("_id"), 8) + "  " + jjmmaaaa(e.getDate("arrivee")) + "  "
                + g(e.get("port"), 12) + "quai " + g(e.get("quai"), 5) + g(e.get("motif"), 12)
                + (depart != null ? "parti le " + jjmmaaaa(depart) : "ENCORE A QUAI");
    }

    private static String ligneR2(Document e) {
        return jjmmaaaa(e.getDate("arrivee")) + "  " + g(e.get("bateau"), 12)
                + "quai " + g(e.get("quai"), 5) + e.getString("motif");
    }

    private static String ligneR3(String port, long tonnes, long nb) {
        return g(port, 12) + d(deci(tonnes, 0), 12) + " t en " + d(nb, 5) + " escales";
    }

    private static String ligneR4(Grosse e) {
        return d(e.id(), 8) + "  " + g(e.bateau(), 12) + g(e.port(), 12)
                + d(deci(e.total(), 0), 9) + " t";
    }
}
