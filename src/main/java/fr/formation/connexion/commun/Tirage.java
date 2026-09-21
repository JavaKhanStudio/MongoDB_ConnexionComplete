package fr.formation.connexion.commun;

import java.util.Date;
import java.util.List;

/**
 * L'aleatoire reproductible — {@code alea()} et {@code tireur()} de
 * {@code sujets/outils.js}, portes bit pour bit.
 *
 * <p>mulberry32 : trente-deux bits d'etat, une graine entiere, et la meme
 * suite partout. {@link Math#random()} n'a pas de graine — il est donc
 * interdit dans ce projet : deux etudiants n'auraient pas la meme base, et
 * les chiffres du README seraient faux chez l'un des deux.
 *
 * <p>BIT POUR BIT, et ce n'est pas une coquetterie : la meme graine doit
 * rendre, au document pres, la base que {@code charger.js} fabrique cote
 * mongosh. Les {@code int} Java debordent exactement comme
 * {@code Math.imul} et les operateurs binaires de JavaScript, qui
 * travaillent eux aussi sur trente-deux bits signes. Et chaque tirage
 * consomme le generateur dans le MEME ORDRE que le .js — un seul appel de
 * plus ou de moins, et toute la suite glisse.
 */
public final class Tirage {

    private int a;

    public Tirage(long graine) {
        this.a = (int) graine;                                  // graine >>> 0
    }

    /** Un reel dans [0, 1[ — {@code r()} du .js. */
    public double r() {
        a += 0x6D2B79F5;
        int t = a;
        t = (t ^ (t >>> 15)) * (t | 1);
        t ^= t + (t ^ (t >>> 7)) * (t | 61);
        return Integer.toUnsignedLong(t ^ (t >>> 14)) / 4294967296.0;
    }

    /** Un entier entre {@code a} et {@code b}, bornes incluses. */
    public int entier(int a, int b) {
        return a + (int) Math.floor(r() * (b - a + 1));
    }

    public <T> T choix(List<T> t) {
        return t.get((int) Math.floor(r() * t.size()));
    }

    public <T> T choix(T[] t) {
        return t[(int) Math.floor(r() * t.length)];
    }

    /** Un choix pondere : {@code pondere(List.of(Poids.de("REUSSIE", 7), Poids.de("ECHOUEE", 3)))}. */
    public <T> T pondere(List<Poids<T>> paires) {
        double total = 0;
        for (Poids<T> p : paires) {
            total += p.poids();
        }
        double x = r() * total;
        for (Poids<T> p : paires) {
            x -= p.poids();
            if (x < 0) {
                return p.valeur();
            }
        }
        return paires.get(paires.size() - 1).valeur();
    }

    /** Une date entre deux dates, a la minute. */
    public Date date(Date debut, Date fin) {
        double ecart = fin.getTime() - debut.getTime();
        return new Date(debut.getTime() + (long) Math.floor(r() * ecart / 60000) * 60000);
    }

    /** Un reel arrondi a {@code dec} decimales — pas de 3.0000000000000004 en base. */
    public double arrondi(double a, double b, int dec) {
        double f = Math.pow(10, dec);
        return Math.round((a + r() * (b - a)) * f) / f;
    }

    /** Vrai avec la probabilite {@code p}. */
    public boolean chance(double p) {
        return r() < p;
    }

    /** Une valeur et son poids, pour {@link #pondere(List)}. */
    public record Poids<T>(T valeur, double poids) {
        public static <T> Poids<T> de(T valeur, double poids) {
            return new Poids<>(valeur, poids);
        }
    }

    /**
     * Un nombre tel que mongosh l'aurait ecrit en base. JavaScript n'a
     * qu'un type de nombre ; le pilote de mongosh en fait un {@code int}
     * BSON quand il est entier et tient sur 32 bits, un {@code double}
     * sinon. Une tonne tiree a 150,00 est donc un {@code int} cote
     * mongosh : pour que les deux chargements rendent la MEME base, type
     * compris, le Java fait pareil.
     */
    public static Object nombre(double v) {
        if (v == Math.rint(v) && v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE
                && !(v == 0 && 1 / v < 0)) {
            return (int) v;
        }
        return v;
    }
}
