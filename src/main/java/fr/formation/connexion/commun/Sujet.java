package fr.formation.connexion.commun;

import java.util.List;

/**
 * Ce que les trois sujets ont en commun — et c'est exactement le contrat
 * de {@code requetes.js} + {@code optimiser.js} du projet mongosh.
 *
 * <p>Un sujet, c'est quatre requetes ecrites de la facon la plus
 * naturelle, les memes une fois l'optimisation posee (une ou deux
 * changent de texte, les autres pas d'un caractere), le geste qui pose
 * les index et ecrit les copies, et une exploration.
 */
public interface Sujet {

    /** « dune », « tortues » ou « bateaux ». */
    String nom();

    /** Les quatre requetes du sujet, telles que l'etudiant les trouve. */
    List<Requete> requetes();

    /**
     * Les memes quatre questions, une fois l'optimisation posee.
     * Sur dune et tortues, une seule change de texte (R3) ; sur bateaux,
     * deux (R3 et R4). Les autres sont rendues a l'identique : seul leur
     * CHEMIN change.
     */
    List<Requete> requetesOptimisees();

    /**
     * Pose les index et ecrit les references etendues, et rend le
     * commentaire de ce qu'elle fait — c'est la correction.
     */
    List<String> optimiser();

    /** L'exercice d'exploration du sujet, joue et commente. */
    List<String> exploration();
}
