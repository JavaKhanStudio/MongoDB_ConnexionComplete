package fr.formation.connexion.dune.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Une region, et ses vers.
 *
 * <p>{@code vers} est un 1:N BORNE — deux ou trois par region, et ce
 * sera tout. Il reste donc DANS la region : ce n'est pas lui qu'on
 * optimise.
 */
@Document(collection = "regions")
public record Region(
        @Id String id,
        String hemisphere,
        Double indiceTempete,
        List<VerDuDesert> vers) {

    public record VerDuDesert(String nom, String statut) {
    }
}
