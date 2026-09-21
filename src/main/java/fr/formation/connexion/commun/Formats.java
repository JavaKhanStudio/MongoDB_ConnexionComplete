package fr.formation.connexion.commun;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * La mise en forme du projet « Optimisation d'une collection », portee
 * telle quelle de {@code sujets/outils.js}.
 *
 * <p>Ce n'est pas de la cosmetique : les lignes que ces fonctions
 * fabriquent sont comparees, CARACTERE PAR CARACTERE, a celles que le
 * chargement a rangees dans la collection {@code reference}. Une colonne
 * d'une largeur differente, et {@link Reference} declare la reponse
 * changee. C'est ce qui prouve que le portage en Java repond bien a la
 * meme question que le mongosh.
 *
 * <p>ATTENTION : le chargement Java ({@link Chargement}) met ses lignes en
 * forme avec ces MEMES fonctions. Changer une largeur ici change donc les
 * deux cotes a la fois, et le juge ne voit rien — mais la collection
 * {@code reference} ne serait plus celle du mongosh, et {@code make
 * comparer} le dit. C'est lui qui garde ce fichier.
 */
public final class Formats {

    private Formats() {
    }

    /** padEnd : la valeur, completee a droite jusqu'a {@code n}. */
    public static String g(Object v, int n) {
        String s = String.valueOf(v);
        return s.length() >= n ? s : s + " ".repeat(n - s.length());
    }

    /** padStart : la valeur, completee a gauche jusqu'a {@code n}. */
    public static String d(Object v, int n) {
        String s = String.valueOf(v);
        return s.length() >= n ? s : " ".repeat(n - s.length()) + s;
    }

    /**
     * 120000 devient « 120 000 ». Un nombre a six chiffres colle est
     * illisible, et tout l'exercice consiste a comparer des nombres a
     * six chiffres.
     */
    public static String n(long x) {
        return String.valueOf(x).replaceAll("\\B(?=(\\d{3})+(?!\\d))", " ");
    }

    /** Un reel, arrondi a {@code dec} decimales, sans notation flottante. */
    public static String deci(double x, int dec) {
        long f = (long) Math.pow(10, dec);
        long e = (long) Math.floor(Math.abs(x) * f + 0.5);
        String s = dec == 0
                ? String.valueOf(e)
                : (e / f) + "." + d0(e % f, dec);
        return (x < 0 ? "-" : "") + s;
    }

    /**
     * La moyenne, arrondie comme le ferait {@code to_char} : somme et
     * nombre sont des entiers, l'arrondi est donc exact et ne depend pas
     * du flottant.
     */
    public static String moyenne(long somme, long nb, int dec) {
        long f = (long) Math.pow(10, dec);
        long e = (long) Math.floor((somme * (double) f * 2 + nb) / (2.0 * nb));
        return (e / f) + "." + d0(e % f, dec);
    }

    /** Une date, en UTC : la base est en UTC, l'enonce aussi. */
    public static String jjmmaaaa(Instant dt) {
        ZonedDateTime z = dt.atZone(ZoneOffset.UTC);
        return d0(z.getDayOfMonth(), 2) + "/" + d0(z.getMonthValue(), 2) + "/" + z.getYear();
    }

    private static String d0(long v, int n) {
        String s = String.valueOf(v);
        return s.length() >= n ? s : "0".repeat(n - s.length()) + s;
    }
}
