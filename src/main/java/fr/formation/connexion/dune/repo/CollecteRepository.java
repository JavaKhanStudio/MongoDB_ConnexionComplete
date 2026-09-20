package fr.formation.connexion.dune.repo;

import fr.formation.connexion.dune.model.Collecte;
import fr.formation.connexion.dune.model.DateEtPuits;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

/**
 * Les collectes, vues par Spring Data.
 *
 * <p>Une requete DERIVEE : Spring lit le nom de la methode et fabrique
 * le filtre. Aucun code a ecrire, et aucun index a la cle — c'est tout
 * le probleme du sujet. En SQL, la cle etrangere vers le contremaitre
 * portait un index, offert avec la contrainte ; ici, rien n'est
 * automatique, et {@code findByContremaitre} balaye les 37 500
 * documents tant que personne n'a pose l'index.
 */
public interface CollecteRepository extends MongoRepository<Collecte, Integer> {

    /** R1 — {@code { contremaitre: ... }} */
    List<Collecte> findByContremaitre(String contremaitre);

    /**
     * R2 — {@code { puits, statut, debut: { $gte, $lt } }}, trie par
     * {@code debut} decroissant.
     *
     * <p>ET LA, LA REQUETE DERIVEE S'ARRETE. Le nom qu'il faudrait —
     * {@code findByPuitsAndStatutAndDebutGreaterThanEqualAndDebutLessThan...}
     * — compile, se deploie, et explose a la premiere execution :
     *
     * <pre>you can't add a second 'debut' expression</pre>
     *
     * <p>Spring construit un {@code Criteria} par mot-cle et les pose
     * cote a cote dans un {@code Document} ; deux conditions sur le MEME
     * champ s'ecrasent. Et {@code Between}, la sortie evidente, ne
     * convient pas : en Spring Data MongoDB il est exclusif des DEUX
     * cotes, alors que l'intervalle du sujet est {@code [debut, fin[} —
     * la collecte du 1er juillet a minuit tomberait.
     *
     * <p>On passe donc a {@code @Query}, et le filtre s'ecrit en clair :
     * c'est, caractere pour caractere, celui de {@code requetes.js}.
     */
    @Query(value = "{ 'puits' : ?0, 'statut' : ?1, 'debut' : { $gte : ?2, $lt : ?3 } }",
           sort = "{ 'debut' : -1 }")
    List<Collecte> echecsDuPuits(String puits, String statut, Instant debut, Instant fin);

    // -----------------------------------------------------------------
    //  L'exploration : la requete couverte
    // -----------------------------------------------------------------

    /**
     * Le geste naturel : une PROJECTION, declaree par le type de retour.
     * Spring Data restreint bien les champs demandes au serveur — mais
     * il garde {@code _id}, que personne n'a demande. Un seul champ hors
     * index, et le serveur doit ouvrir le document : l'etage FETCH
     * revient, et {@code totalDocsExamined} avec lui.
     */
    List<DateEtPuits> findProjectedByContremaitre(String contremaitre);

    /**
     * La meme, avec {@code _id} explicitement exclu. La projection ne
     * demande plus QUE des champs que l'index porte : il n'y a plus rien
     * a aller chercher, le plan passe en IXSCAN couvrant et le serveur
     * ne lit aucun document.
     *
     * <p>C'est la seule facon de l'ecrire en Spring Data : la projection
     * derivee du type de retour ne sait pas retirer {@code _id}, il faut
     * la poser a la main dans {@code fields}.
     */
    @Query(value = "{ 'contremaitre' : ?0 }", fields = "{ '_id' : 0, 'debut' : 1, 'puits' : 1 }")
    List<DateEtPuits> couverteParContremaitre(String contremaitre);
}
