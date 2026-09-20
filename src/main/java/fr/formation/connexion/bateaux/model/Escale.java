package fr.formation.connexion.bateaux.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Une escale : un bateau, un port, un quai, des dates.
 *
 * <p>{@code depart} ABSENT veut dire que le bateau est ENCORE A QUAI.
 * C'est une information portee par l'absence du champ, pas par une
 * valeur — cote Java, {@code null}, et {@code ligneR1} l'ecrit.
 *
 * <p>{@code cargaisons} est un 1:N ILLIMITE... qui reste embarque, et
 * c'est le bon choix : une cargaison n'existe pas sans son escale, et
 * personne ne la lit sans elle. Ce qui ne tient pas, c'est le TOTAL —
 * il n'est ecrit nulle part et se recalcule a chaque fois, pour les
 * 94 500 escales.
 *
 * <p>{@code pavillon} et {@code tonnesTotal} n'existent pas au
 * chargement : la correction les ecrit. Le premier est une reference
 * etendue (copie d'un champ de {@code bateaux}), le second un CHAMP
 * CALCULE — une copie de rien du tout, juste le resultat d'un calcul
 * qu'on refuse de refaire a chaque lecture.
 */
@Document(collection = "escales")
public record Escale(
        @Id Integer id,
        Instant arrivee,
        Instant depart,
        String motif,
        String bateau,
        String port,
        String quai,
        List<Cargaison> cargaisons,

        // ajoutes par la correction, absents au chargement
        String pavillon,
        Double tonnesTotal) {

    public record Cargaison(String marchandise, Double tonnes) {
    }
}
