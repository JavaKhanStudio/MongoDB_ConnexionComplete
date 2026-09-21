# Connexion complète

> Les trois corrections de « **Optimisation d'une collection** », qui étaient du
> mongosh, écrites cette fois en **Spring Data MongoDB**. Un dépôt, une racine
> commune, trois paquets, **trois points d'entrée Spring**, et un dossier Bruno
> par application. Accompagne la slide « Connexion complète » du deck MongoDB.

---

## Récupérer le projet

```bash
git clone https://github.com/JavaKhanStudio/MongoDB_ConnexionComplete.git
cd MongoDB_ConnexionComplete
```

Tout tient sur `main` : il n'y a pas de branche `correction` ici, ce dépôt
**est** la correction.

---

## Ce que ce dépôt contient

Les requêtes, la correction, **et les données** — pas sous forme de dump :
chaque application sait refaire sa base à partir d'une graine. Le dépôt se
suffit à lui-même : son propre MongoDB (`docker/docker-compose.yml`, port
**27061**), ses trois chargements en Java, ses trois API.

C'est le même tirage que [`MongoDB_Optimisation`](https://github.com/JavaKhanStudio/MongoDB_Optimisation),
porté bit pour bit : **même graine, même base** — la même, au document près et
au type de chaque nombre près, que celle que les `charger.js` fabriquent en
`mongosh`. Les chiffres de ce README sont donc ceux du projet mongosh, et
`make comparer` le vérifie.

```bash
make bases          # démarre MongoDB (27061), compile, et fabrique dune, tortues, bateaux
make dune           # http://localhost:8081
```

`make aide` liste tout. `make bases VOLUME=10` en met dix fois plus ;
`make charger-dune` refait une seule base, en deux secondes.

Le chargement, c'est la même application lancée autrement :

```bash
java -jar target/connexion-complete-1.0.0-dune.jar --charger [--volume=10] [--graine=20260920]
```

Avec `--charger`, pas de serveur web : elle jette la base, la refait, et rend
la main (`commun/Lancement.java`).

---

## Les trois applications

| application | port | base | paquet | ce qu'on y apprend |
|---|---|---|---|---|
| `dune` | 8081 | `dune` | `fr.formation.connexion.dune` | l'index simple · l'ordre des clés d'un index composé · **la référence étendue** · le tri bloquant · *(exploration)* la requête couverte |
| `tortues` | 8082 | `tortues` | `fr.formation.connexion.tortues` | la clé étrangère que SQL indexait tout seul · l'index **multiclé** · la copie de copie · la **sélectivité** · *(exploration)* `$elemMatch` |
| `bateaux` | 8083 | `bateaux` | `fr.formation.connexion.bateaux` | deux requêtes sur quatre qu'**aucun index** ne peut servir · la référence étendue · le **champ calculé** · *(exploration)* l'index **partiel** |

Elles sont indépendantes : une base chacune, un port chacun, n'importe quel
ordre. On peut les lancer toutes les trois en même temps — mais **pas mesurer
sur deux à la fois** : le banc lit des compteurs de *serveur*, et deux mesures
simultanées se mélangent.

---

## Les sept endpoints, les mêmes pour les trois

`<s>` vaut `dune`, `tortues` ou `bateaux`.

| endpoint | l'équivalent mongosh | ce qu'il fait |
|---|---|---|
| `GET /api/<s>/etat` | — | dans quel état est la base : les index posés, ou « point de départ » |
| `GET /api/<s>/mesurer` | `make <s>-mesurer` | **le banc** : les quatre requêtes jouées pour de vrai |
| `GET /api/<s>/requetes/R1` | — | une requête seule, **avec ses lignes** |
| `POST /api/<s>/optimiser` | `make <s>-optimiser` | **la correction** : remet à nu, mesure, pose, remesure, compare |
| `POST /api/<s>/remettre` | `make <s>-remettre` | tout défaire, sans recharger |
| `GET /api/<s>/index` | — | les index posés, `_id` mis à part |
| `GET /api/<s>/exploration` | — | l'exercice d'exploration du sujet |

`optimiser` et `remettre` sont en **POST** parce qu'ils écrivent — index, champs
recopiés sur des dizaines de milliers de documents. Un GET ne doit jamais faire
ça : un navigateur ou un proxy le rejouerait tout seul.

---

## Les dossiers Bruno

Un par application, sous `bruno/<sujet>/`. Ce sont des fichiers texte versionnés
avec le projet : l'interface graphique de Bruno ouvre le dossier, il n'y a rien
à importer ni à synchroniser.

```bash
make bruno-dune          # ou : cd bruno/dune && bru run --env local
make bruno               # les trois, à la suite
```

Chacune porte **21 assertions** : le code HTTP, le nombre de lignes rendues par
chaque requête, et surtout `reponseInchangee`. `bru run` est donc une vraie
vérification, pas une démonstration — si le portage Spring pose une autre
question que le mongosh, elle tombe.

```
  Requests      11 (11 Passed)
  Assertions    21/21
```

L'environnement `local` pointe sur le bon port. Les dossiers sont numérotés
dans l'ordre où on les joue : `00` l'état, `01` les quatre requêtes une par
une, `02` le banc, `03` la correction — dont `05-remettre` qui défait tout.

---

## Ce que la correction rend

`POST /api/dune/optimiser`, sur la base au point de départ :

```
  code  ce qu elle demande                           lus avant   lus apres     gain   ms avant  ms apres
  R1    Les collectes d un contremaitre                 37 500         169    222 x         18        12
  R2    Les echecs d un puits, du plus recent           37 500           7   5357 x         11         3
  R3    Ce qu une region a sorti sur un mois            38 585         101    382 x         19         4
  R4    Les dix plus fortes secousses d un puits        62 500          10   6250 x         17         3
```

Ce sont, au document près, les chiffres de `make dune-optimiser` côté mongosh.
Le « avant » n'est **pas un chiffre recopié** : la correction commence par
remettre la base à nu et le mesurer, à chaque passage.

| sujet | R1 | R2 | R3 | R4 |
|---|---|---|---|---|
| `dune` | 222 x | 5 357 x | 382 x | 6 250 x |
| `tortues` | 119 x | 503 x | 1 377 x | **7,4 x** |
| `bateaux` | 3 780 x | 98 x | 36 x | 9 450 x |

Le 7,4 x de `tortues` est la leçon du sujet, pas un raté : un index ne paie que
ce qu'il écarte, et `{ tags: 1 }` n'écarte que huit documents sur dix. Le
chiffre qui décide s'appelle la **sélectivité**.

---

## Le juge

Optimiser, c'est changer le chemin **sans changer la réponse**.

Le chargement a enregistré, dans la collection `reference` de chaque base, ce
que chaque requête répond — accumulé au vol pendant qu'il fabrique les
documents, *pas* en rejouant les requêtes du sujet. C'est **cette
collection-là** que le banc interroge :

```
=== La reponse n a pas change ? =======================================
  Les 4 reponses sont celles du chargement : optimise, pas change.
```

Deux choses en découlent, et les deux comptent :

* un index ne change jamais une réponse ; **une référence étendue mal recopiée,
  si**. Le banc le voit tout de suite ;
* le portage Spring est jugé par le **même arbitre** que le mongosh. Les lignes
  que les requêtes Spring mettent en forme doivent être, caractère pour
  caractère, celles du chargement. Si elles ne le sont pas, la requête Spring
  ne pose pas la même question — et c'est un bug du portage, pas du serveur.

Et l'arbitre lui-même est gardé : la collection `reference` que le Java écrit
est, ligne pour ligne, celle que `charger.js` écrit côté mongosh.

---

## `make comparer` — les deux chargements rendent-ils la même base ?

La preuve du portage des chargements, et le garde-fou de `commun/Formats.java`.
Il demande le dépôt voisin (`OPTIMISATION=../MongoDB_Optimisation` par défaut) :
chaque base est fabriquée deux fois sur **son** serveur (27051), par
`charger.js` sous son nom et par le Java sous `j_<sujet>`, puis
`tools/comparer-chargements.js` compare chaque collection, document par
document, en EJSON **canonique** — l'ordre des champs et le type de chaque
nombre compris.

```
  == dune : mongosh contre Java
  ok  collectes                37500 documents, identiques
  ...
  ok  reference                    4 documents, identiques
  dune et j_dune : la meme base, type compris.
```

Le type compte : JavaScript n'a qu'une sorte de nombre, et mongosh en fait un
`int` BSON quand il est entier, un `double` sinon. Une tonne tirée à 150,00 est
un `int` côté mongosh ; le Java fait pareil (`Tirage.nombre`).

Pourquoi il faut ce garde-fou : chargement et requêtes mettent leurs lignes en
forme avec les **mêmes** `Formats`. Changer une largeur de colonne y change les
deux côtés à la fois, et le juge ne voit rien — `make comparer`, si.

Essayer, sur une base au point de départ :
`GET /api/dune/mesurer?optimisees=true`. R3 rend 0 ligne au lieu de 5, et le
juge répond `reponseInchangee: false`. La réécriture seule ne suffit pas.

---

## Comment on mesure

Les colonnes `lus` et `cles` ne sortent **pas** d'`explain()`. Elles sortent des
compteurs du serveur — `serverStatus().metrics.queryExecutor` — relevés avant et
après la requête. Ils comptent **tout**, d'un seul nombre, y compris ce qu'un
`$lookup` va chercher tout seul.

`explain()`, lui, éparpille ses comptes dans l'arbre : sur R3 de `dune`, l'étage
`$cursor` en annonce 37 500 et le `$lookup` 1 085, et seul le sommet porte la
somme. Lire le mauvais nœud fait mentir la mesure.

---

## Un module Maven, trois points d'entrée

Une seule arborescence de sources, un seul `pom.xml`, et **trois classes `main`**.

```
src/main/java/fr/formation/connexion/
    commun/      le banc, le juge, les formats, explain()   ← la racine partagée
    dune/        DuneApplication      (8081)
    tortues/     TortuesApplication   (8082)
    bateaux/     BateauxApplication   (8083)
```

Trois choses tiennent la séparation :

* `scanBasePackages = { "…commun", "…dune" }` limite le scan. Sans ça, Spring
  monterait les trois jeux de services d'un coup, et `MongoTemplate` pointerait
  sur une base pour trois modèles ;
* `@EnableMongoRepositories(basePackages = "…dune.repo")` désigne explicitement
  les repositories du sujet — déclaré, il remplace celui que l'auto-configuration
  aurait posé sur tout le classpath ;
* le profil (`dune`, `tortues`, `bateaux`), ajouté au démarrage, apporte le port
  et le nom de la base (`application-<sujet>.yml`).

Côté build, le plugin Spring Boot produit **trois jars**, un par `classifier` :

```
target/connexion-complete-1.0.0-dune.jar
target/connexion-complete-1.0.0-tortues.jar
target/connexion-complete-1.0.0-bateaux.jar
```

L'exécution `repackage` héritée du parent est désactivée : avec trois classes
`main`, elle ne saurait pas laquelle prendre.

---

## Arborescence

```
pom.xml                     un module, trois jars
Makefile                    make aide
docker/docker-compose.yml   le MongoDB du dépôt, sur 27061
tools/comparer-chargements.js   make comparer : Java contre mongosh, document par document
src/main/resources/
    application.yml         ce qui est commun — dont auto-index-creation: false
    application-<s>.yml     le port et la base de chaque application
src/main/java/fr/formation/connexion/
    commun/
        Banc.java           LE BANC : les compteurs du serveur, le plan, les tableaux
        Reference.java      LE JUGE : la collection `reference`, écrite au chargement
        Tirage.java         mulberry32 : l'aléatoire à graine, bit pour bit celui de outils.js
        Chargement.java     jeter la base et la refaire — le squelette des trois charger.js
        Lancement.java      servir, ou --charger
        Bases.java          les index posés, et « tout défaire »
        Explications.java   explain(), que Spring Data n'expose pas
        Formats.java        g / d / n / deci / moyenne / jjmmaaaa, portés de outils.js
        Etapes.java         un étage d'agrégation écrit en BSON, tel quel
        Sujet.java          le contrat : quatre requêtes, la correction, l'exploration
        SujetController.java  les sept endpoints, une seule fois pour les trois
    <sujet>/
        <S>Application.java   le point d'entrée
        chargement/<S>Chargement.java   LA BASE, tirée de la graine (portage de charger.js)
        model/                les @Document — et le document polymorphe en Java
        repo/                 les MongoRepository, et là où la requête dérivée s'arrête
        service/
            <S>Requetes.java      LES QUATRE REQUETES   (portage de requetes.js)
            <S>Optimisation.java  LA CORRECTION          (portage de optimiser.js)
            <S>Sujet.java         la colle
        web/<S>Controller.java    @RequestMapping("/api/<sujet>")
bruno/<sujet>/              une collection Bruno par application
```

---

## Ce que l'écriture a appris

| constat | où |
|---|---|
| **La requête dérivée s'arrête à l'intervalle.** `findBy…DebutGreaterThanEqualAndDebutLessThan…` compile, se déploie, et explose à la première exécution : *you can't add a second 'debut' expression*. Spring construit un `Criteria` par mot-clé et les pose côte à côte dans un `Document` ; deux conditions sur le même champ s'écrasent. Et `Between`, la sortie évidente, est exclusif des **deux** côtés — l'intervalle du sujet est `[début, fin[`. Les trois R2 passent donc à `@Query`. | `*/repo/*Repository.java` |
| **La projection dérivée ne sait pas retirer `_id`.** Un type de retour projeté restreint bien les champs demandés au serveur, mais garde `_id` — et un seul champ hors index oblige le serveur à ouvrir le document. La requête couverte ne s'obtient qu'avec `@Query(fields = "{ '_id' : 0, … }")`. C'est visible dans `GET /api/dune/exploration` : 169 documents lus contre 0. | `dune/repo/CollecteRepository.java` |
| **`auto-index-creation` doit rester à `false`, explicitement.** Si Spring posait les index au démarrage à partir de `@Indexed`, la mesure « avant » serait déjà optimisée et on ne verrait jamais le `COLLSCAN`. | `application.yml` |
| **`plan()` ment sur la requête couverte.** Le parcours de tout l'arbre d'`explain` ramasse aussi les `rejectedPlans`, dont un porte un `FETCH` : l'arbre entier répond « IXSCAN » là où le plan retenu dit `PROJECTION_COVERED`. Le banc s'en accommode (c'est ce que fait le mongosh) ; l'exploration, non — elle lit `queryPlanner.winningPlan`. | `commun/Banc.java` |
| **Un pipeline qui écrit se donne au driver tel quel.** `$merge` n'a rien à rendre : `toCollection()` le joue, là où `MongoTemplate` voudrait mapper un résultat qui n'existe pas. Idem pour `$sum` imbriqué dans `$sum` — une traduction approximative changerait la réponse sans le dire. | `*/service/*Optimisation.java`, `commun/Etapes.java` |
| **Le document polymorphe est plus souple que le record.** Une collecte `REUSSIE` n'a pas de `cause` ; le record a le nombre de composants qu'il a. Ce qui est absent arrive à `null` — et ressort **absent** du JSON, parce que `default-property-inclusion: non_null` est posé. Pas de `@TypeAlias` ni de `_class` : le chargement écrit des `Document` bruts, comme mongosh — le modèle Java lit une base qui ne sait rien de lui. | `*/model/*.java` |
| **Le banc suppose qu'on est seul sur le serveur.** `serverStatus().metrics.queryExecutor` est global : deux navigateurs qui rafraîchissent en même temps, et les deux mesures se mélangent. C'est le prix d'un compteur qui, lui, ne dépend pas de la forme du plan. | `commun/Banc.java` |
| **Le tirage se porte au bit près, pas à peu près.** mulberry32 tient sur des `int` : Java déborde exactement comme `Math.imul` et les opérateurs binaires de JavaScript. Mais chaque tirage doit être consommé dans le **même ordre** que le .js — `for (k = 0; k < t.entier(2, 3); k++)` retire la borne à chaque tour, le `++k` des capitaines avance deux fois, `brevet` est tiré *après* les commandements. Un appel de plus ou de moins, et toute la suite glisse. | `*/chargement/*Chargement.java`, `commun/Tirage.java` |
