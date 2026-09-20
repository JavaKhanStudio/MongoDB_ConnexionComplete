package fr.formation.connexion.commun;

import java.util.List;

/**
 * Ce qu'un endpoint rend : le tableau que mongosh aurait imprime, les
 * memes chiffres en structure, et le verdict du juge.
 *
 * <p>Les champs nuls ne sortent pas du JSON
 * ({@code default-property-inclusion: non_null}) : une reponse ne porte
 * que ce qui la concerne.
 */
public record Rapport(
        String sujet,
        String titre,
        List<String> tableau,
        List<Mesure> mesures,
        Boolean reponseInchangee,
        List<String> notes,
        List<String> lignes) {

    public static Rapport de(String sujet, String titre, List<String> notes) {
        return new Rapport(sujet, titre, null, null, null, notes, null);
    }
}
