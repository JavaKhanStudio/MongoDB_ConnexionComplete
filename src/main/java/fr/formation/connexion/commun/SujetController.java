package fr.formation.connexion.commun;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Les memes sept endpoints pour les trois sujets — c'est le
 * {@code Makefile} du projet mongosh, en HTTP.
 *
 * <table>
 *   <caption>La correspondance</caption>
 *   <tr><td>{@code make <sujet>-mesurer}</td>  <td>{@code GET  /api/<sujet>/mesurer}</td></tr>
 *   <tr><td>{@code make <sujet>-optimiser}</td><td>{@code POST /api/<sujet>/optimiser}</td></tr>
 *   <tr><td>{@code make <sujet>-remettre}</td> <td>{@code POST /api/<sujet>/remettre}</td></tr>
 * </table>
 *
 * <p>{@code optimiser} et {@code remettre} sont en POST parce qu'ils
 * ECRIVENT — l'un pose des index et recopie un champ sur des dizaines de
 * milliers de documents, l'autre les enleve. Un GET ne doit jamais faire
 * ca : un navigateur, un proxy ou un prefetch le rejouerait tout seul.
 */
public abstract class SujetController {

    protected final Banc banc;
    protected final Reference reference;
    protected final Bases bases;

    protected SujetController(Banc banc, Reference reference, Bases bases) {
        this.banc = banc;
        this.reference = reference;
        this.bases = bases;
    }

    protected abstract Sujet sujet();

    /** La base repond-elle, et dans quel etat est-elle. */
    @GetMapping("/etat")
    public Rapport etat() {
        List<String> notes = new ArrayList<>();
        List<String> index = bases.poses();
        notes.add("  base            " + sujet().nom());
        notes.add("  index poses     " + (index.isEmpty() ? "aucun (hors _id) — base au point de depart" : index.size()));
        notes.addAll(index);
        return Rapport.de(sujet().nom(), "Etat de la base", notes);
    }

    /** Le banc : les quatre requetes, jouees pour de vrai. */
    @GetMapping("/mesurer")
    public Rapport mesurer(@RequestParam(defaultValue = "false") boolean optimisees) {
        List<Mesure> mesures = banc.jouer(optimisees ? sujet().requetesOptimisees() : sujet().requetes());
        Reference.Verdict verdict = reference.verifier(mesures);
        return new Rapport(sujet().nom(),
                optimisees ? "Les quatre questions, version optimisee" : "Les quatre requetes, telles quelles",
                Banc.tableau(mesures), mesures, verdict.ok(), verdict.lignes(), null);
    }

    /** Une requete seule, avec ses lignes : de quoi lire la reponse. */
    @GetMapping("/requetes/{code}")
    public Rapport une(@PathVariable String code,
                       @RequestParam(defaultValue = "false") boolean optimisee) {
        List<Requete> liste = optimisee ? sujet().requetesOptimisees() : sujet().requetes();
        Requete r = liste.stream()
                .filter(x -> x.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ce sujet a R1, R2, R3 et R4 — pas de " + code));
        Mesure m = banc.jouerUne(r);
        return new Rapport(sujet().nom(), m.code() + " — " + m.intitule(),
                Banc.tableau(List.of(m)), List.of(m), null, null, m.lignes());
    }

    /**
     * LA CORRECTION. Elle remet d'abord la base a nu et MESURE : le
     * « avant » n'est jamais un chiffre recopie, il est refait a chaque
     * passage. Puis elle pose, remesure, verifie que la reponse n'a pas
     * bouge, et met les deux tableaux cote a cote.
     */
    @PostMapping("/optimiser")
    public Rapport optimiser() {
        List<String> notes = new ArrayList<>();
        notes.add("=== On repart de la base nue " + "=".repeat(42));
        notes.addAll(bases.remettre());

        List<Mesure> avant = banc.jouer(sujet().requetes());
        notes.add("");
        notes.add("=== Avant " + "=".repeat(61));
        notes.addAll(Banc.tableau(avant));

        notes.add("");
        notes.addAll(sujet().optimiser());

        List<Mesure> apres = banc.jouer(sujet().requetesOptimisees());
        notes.add("");
        notes.add("=== Les memes quatre questions, sur la base optimisee " + "=".repeat(18));
        notes.addAll(Banc.tableau(apres));

        Reference.Verdict verdict = reference.verifier(apres);
        notes.add("");
        notes.add("=== La reponse n a pas change ? " + "=".repeat(39));
        notes.addAll(verdict.lignes());

        notes.add("");
        notes.add("=== Avant / apres " + "=".repeat(53));
        notes.addAll(Banc.compare(avant, apres));

        notes.add("");
        notes.add("=== Les index poses " + "=".repeat(51));
        notes.addAll(bases.poses());
        notes.add("");
        notes.add("  Tout defaire :   POST /api/" + sujet().nom() + "/remettre");

        return new Rapport(sujet().nom(), "La correction", Banc.tableau(apres), apres,
                verdict.ok(), notes, null);
    }

    /** Tout defaire, sans recharger la base. */
    @PostMapping("/remettre")
    public Rapport remettre() {
        List<String> notes = new ArrayList<>(bases.remettre());
        notes.add("");
        notes.add("  La base est celle du chargement.   GET /api/" + sujet().nom() + "/mesurer");
        return Rapport.de(sujet().nom(), "On remet la base comme elle etait", notes);
    }

    /** Les index poses, _id mis a part. */
    @GetMapping("/index")
    public Rapport index() {
        List<String> poses = bases.poses();
        return Rapport.de(sujet().nom(), "Les index poses",
                poses.isEmpty() ? List.of("  aucun index hors _id.") : poses);
    }

    /** L'exercice d'exploration du sujet. */
    @GetMapping("/exploration")
    public Rapport exploration() {
        return Rapport.de(sujet().nom(), "Exploration", sujet().exploration());
    }
}
