package fr.formation.connexion.commun;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Ce qu'une requete a coute, une fois jouee pour de vrai.
 *
 * <p>{@code rendus} est ce que la question demande : il ne bouge pas.
 * {@code lus} est ce que le serveur a du parcourir pour le trouver :
 * c'est lui qu'on fait tomber. Le jour ou les deux se ressemblent, la
 * requete est optimisee.
 *
 * <p>{@code lignes} ne sort pas en JSON — 1 344 lignes dans un tableau
 * de mesures le rendraient illisible. Chaque requete a son endpoint a
 * elle pour les montrer.
 */
public record Mesure(
        String code,
        String intitule,
        int rendus,
        long lus,
        long cles,
        long ms,
        String plan,
        @JsonIgnore List<String> lignes) {
}
