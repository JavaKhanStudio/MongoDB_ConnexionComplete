package fr.formation.connexion.dune.model;

import java.time.Instant;

/**
 * Une projection : la date et le puits d'une collecte, et rien d'autre.
 *
 * <p>Une interface, pas un record : Spring Data fabrique un proxy qui ne
 * lit que ce que les accesseurs demandent. Ce que cette projection ne
 * peut plus rendre, c'est le tonnage — il faudrait une quatrieme cle
 * dans l'index, a tenir a jour a chaque ecriture sur 37 500 documents.
 */
public interface DateEtPuits {

    Instant getDebut();

    String getPuits();
}
