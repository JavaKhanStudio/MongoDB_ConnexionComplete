# =====================================================================
#  Connexion complete — les trois corrections de « Optimisation d'une
#  collection », portees en Spring Data MongoDB.
#  make aide
# =====================================================================
#  UN depot, UN module Maven, TROIS applications Spring.
#
#  Les DONNEES ne sont pas ici : elles viennent du depot
#  MongoDB_Optimisation, qui les fabrique a partir d'une graine. C'est
#  tout le sens de « Connexion complete » — le projet pur MongoDB
#  fabrique la base, le projet Spring s'y branche.

OPTIMISATION ?= ../MongoDB_Optimisation
MVN           = ./mvnw
VERSION       = 1.0.0
JAR           = target/connexion-complete-$(VERSION)

.PHONY: aide bases etat construire dune tortues bateaux tout \
        bruno-dune bruno-tortues bruno-bateaux bruno propre

aide:
	@echo ""
	@echo "  Mise en route, dans cet ordre"
	@echo "    make bases             - demarre MongoDB et charge les trois bases"
	@echo "                             (delegue a $(OPTIMISATION))"
	@echo "    make construire        - compile et fabrique les trois jars"
	@echo "    make dune              - lance l application « dune »     (8081)"
	@echo "    make tortues           - lance l application « tortues »  (8082)"
	@echo "    make bateaux           - lance l application « bateaux »  (8083)"
	@echo ""
	@echo "  Les trois applications sont independantes : une base chacune,"
	@echo "  un port chacune. On peut les lancer toutes les trois a la fois,"
	@echo "  mais PAS mesurer sur deux en meme temps — le banc lit des"
	@echo "  compteurs de SERVEUR, et deux mesures simultanees se melangent."
	@echo ""
	@echo "  Les tester"
	@echo "    make bruno-dune        - joue la collection Bruno du sujet"
	@echo "    make bruno             - les trois, l une apres l autre"
	@echo "                             (demande les trois applications lancees)"
	@echo ""
	@echo "    make etat              - MongoDB repond-il, et sur quelles bases"
	@echo "    make propre            - efface target/"
	@echo ""
	@echo "  Les endpoints, pour chaque sujet <s> = dune | tortues | bateaux :"
	@echo "    GET  /api/<s>/etat              dans quel etat est la base"
	@echo "    GET  /api/<s>/mesurer           LE BANC : les quatre requetes"
	@echo "    GET  /api/<s>/requetes/R1       une requete, avec ses lignes"
	@echo "    POST /api/<s>/optimiser         LA CORRECTION (ecrit)"
	@echo "    POST /api/<s>/remettre          tout defaire (ecrit)"
	@echo "    GET  /api/<s>/index             les index poses"
	@echo "    GET  /api/<s>/exploration       l exercice d exploration"
	@echo ""

# --------------------------------------------------------------------
# Les donnees viennent d'ailleurs, et c'est voulu.
# --------------------------------------------------------------------
bases:
	@test -d $(OPTIMISATION) || { \
	  echo ""; \
	  echo "  Les trois bases se fabriquent dans MongoDB_Optimisation, pas ici."; \
	  echo ""; \
	  echo "      git clone https://github.com/JavaKhanStudio/MongoDB_Optimisation.git $(OPTIMISATION)"; \
	  echo ""; \
	  echo "  Ou, si le depot est ailleurs :   make bases OPTIMISATION=/chemin/vers/lui"; \
	  echo ""; \
	  exit 1; }
	$(MAKE) -C $(OPTIMISATION) demarrer
	$(MAKE) -C $(OPTIMISATION) tout
	@echo ""
	@echo "  Trois bases chargees sur mongodb://localhost:27051 : dune, tortues, bateaux."
	@echo "  Enchainer :   make construire && make dune"

# Une seule ligne : mongosh recoit le script tel quel, sans que make
# s'en mele. Coupee en continuations, elle arrive tronquee.
ETAT_JS = print("  MongoDB     " + db.version()); ["dune","tortues","bateaux"].forEach(function(b){ var d = db.getSiblingDB(b); var n = d.getCollectionNames().length; var ix = d.getCollectionNames().reduce(function(s,c){ return s + d.getCollection(c).getIndexes().filter(function(i){return i.name!=="_id_";}).length; }, 0); print("  " + b.padEnd(12) + n + " collections, " + ix + " index hors _id"); })

etat:
	@docker exec optimisation-mongo mongosh --quiet --eval '$(ETAT_JS)' 2>/dev/null || echo "  MongoDB     ARRETE   (make bases)"

# --------------------------------------------------------------------
# Un module, trois jars : le plugin Spring Boot en produit un par
# classifier, chacun avec son point d'entree.
# --------------------------------------------------------------------
construire:
	$(MVN) -q package -DskipTests
	@ls -1 $(JAR)-*.jar

dune:    ; java -jar $(JAR)-dune.jar
tortues: ; java -jar $(JAR)-tortues.jar
bateaux: ; java -jar $(JAR)-bateaux.jar

# --------------------------------------------------------------------
# Les collections Bruno. `bru` vient de @usebruno/cli ; l'interface
# graphique ouvre les memes dossiers, il n'y a rien a importer.
# --------------------------------------------------------------------
bruno-dune:    ; cd bruno/dune    && bru run --env local
bruno-tortues: ; cd bruno/tortues && bru run --env local
bruno-bateaux: ; cd bruno/bateaux && bru run --env local

bruno: bruno-dune bruno-tortues bruno-bateaux

propre:
	$(MVN) -q clean
