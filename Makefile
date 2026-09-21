# =====================================================================
#  Connexion complete — les trois corrections de « Optimisation d'une
#  collection », portees en Spring Data MongoDB.
#  make aide
# =====================================================================
#  UN depot, UN module Maven, TROIS applications Spring — et leur propre
#  MongoDB (docker/docker-compose.yml, port 27061).
#
#  Les DONNEES ne sont pas un dump : chaque application sait refaire sa
#  base a partir d'une graine (java -jar ... --charger). Meme graine, meme
#  base — la meme, au document pres, que celle que MongoDB_Optimisation
#  fabrique en mongosh (make comparer le prouve).
#
# Sous Podman rootless (Fedora), avant tout :
#   export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock

COMPOSE       = docker compose -f docker/docker-compose.yml
MVN           = ./mvnw
VERSION       = 1.0.0
JAR           = target/connexion-complete-$(VERSION)

# Combien de donnees. VOLUME=1 : la base dont le README donne les
# chiffres. VOLUME=10 en met dix fois plus, et l'ecart se creuse.
VOLUME ?= 1
# La graine du tirage. Meme graine, meme base, partout.
GRAINE ?= 20260920

# Le depot mongosh, seulement pour « make comparer ».
OPTIMISATION ?= ../MongoDB_Optimisation

.PHONY: aide demarrer arreter purger bases charger-dune charger-tortues charger-bateaux \
        etat construire dune tortues bateaux \
        bruno-dune bruno-tortues bruno-bateaux bruno comparer propre

aide:
	@echo ""
	@echo "  Mise en route, dans cet ordre"
	@echo "    make bases             - demarre MongoDB (27061), compile, et fabrique"
	@echo "                             les trois bases. VOLUME=10 pour dix fois plus."
	@echo "    make dune              - lance l application « dune »     (8081)"
	@echo "    make tortues           - lance l application « tortues »  (8082)"
	@echo "    make bateaux           - lance l application « bateaux »  (8083)"
	@echo ""
	@echo "  Les trois applications sont independantes : une base chacune,"
	@echo "  un port chacune. On peut les lancer toutes les trois a la fois,"
	@echo "  mais PAS mesurer sur deux en meme temps — le banc lit des"
	@echo "  compteurs de SERVEUR, et deux mesures simultanees se melangent."
	@echo ""
	@echo "  Une base seule"
	@echo "    make charger-dune      - la jette et la refait (tortues, bateaux)"
	@echo ""
	@echo "  Les tester"
	@echo "    make bruno-dune        - joue la collection Bruno du sujet"
	@echo "    make bruno             - les trois, l une apres l autre"
	@echo "                             (demande les trois applications lancees)"
	@echo "    make comparer          - le chargement Java rend-il la base du mongosh ?"
	@echo "                             (demande $(OPTIMISATION), demarre et charge)"
	@echo ""
	@echo "    make construire        - compile et fabrique les trois jars"
	@echo "    make etat              - MongoDB repond-il, et sur quelles bases"
	@echo "    make arreter           - arrete MongoDB, conserve les donnees"
	@echo "    make purger            - arrete et EFFACE le volume"
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
# MongoDB, et les trois bases.
# --------------------------------------------------------------------
demarrer:
	$(COMPOSE) up -d
	@echo "  attente du serveur..."
	@for i in $$(seq 1 60); do \
	  docker exec connexion-mongo mongosh --quiet --eval 'db.runCommand({ping:1})' > /dev/null 2>&1 && break; sleep 1; done

arreter: ; $(COMPOSE) down
purger:  ; $(COMPOSE) down -v

# Charger une base : on la jette et on la refait. Les donnees se
# FABRIQUENT — meme graine, meme base — donc recommencer ne coute que
# deux secondes, et jamais une surprise. Pas de serveur web : l'application
# charge, et rend la main.
CHARGER = @java -jar $(JAR)-$(1).jar --charger --volume=$(VOLUME) --graine=$(GRAINE) \
            --spring.main.banner-mode=off --logging.level.root=WARN

charger-dune:    ; $(call CHARGER,dune)
charger-tortues: ; $(call CHARGER,tortues)
charger-bateaux: ; $(call CHARGER,bateaux)

bases: demarrer construire
	@$(MAKE) --no-print-directory charger-dune charger-tortues charger-bateaux
	@echo ""
	@echo "  Trois bases chargees sur mongodb://localhost:27061 : dune, tortues, bateaux."
	@echo "  Enchainer :   make dune"

# Une seule ligne : mongosh recoit le script tel quel, sans que make
# s'en mele. Coupee en continuations, elle arrive tronquee.
ETAT_JS = print("  MongoDB     " + db.version()); ["dune","tortues","bateaux"].forEach(function(b){ var d = db.getSiblingDB(b); var n = d.getCollectionNames().length; var ix = d.getCollectionNames().reduce(function(s,c){ return s + d.getCollection(c).getIndexes().filter(function(i){return i.name!=="_id_";}).length; }, 0); print("  " + b.padEnd(12) + n + " collections, " + ix + " index hors _id"); })

etat:
	@docker exec connexion-mongo mongosh --quiet --eval '$(ETAT_JS)' 2>/dev/null || echo "  MongoDB     ARRETE   (make bases)"

# --------------------------------------------------------------------
# La preuve du portage des chargements. On fabrique chaque base DEUX
# fois sur le serveur de MongoDB_Optimisation (27051) : par son
# charger.js, sous son nom, et par le Java, sous le nom j_<sujet>. Puis
# tools/comparer-chargements.js les compare document par document, type
# des nombres compris. Les bases j_<sujet> sont jetees a la fin.
# --------------------------------------------------------------------
comparer: construire
	@test -d $(OPTIMISATION) || { echo "  make comparer demande $(OPTIMISATION)   (OPTIMISATION=/chemin)"; exit 1; }
	$(MAKE) -C $(OPTIMISATION) demarrer tout VOLUME=$(VOLUME) GRAINE=$(GRAINE)
	@for s in dune tortues bateaux; do \
	  java -jar $(JAR)-$$s.jar --charger --volume=$(VOLUME) --graine=$(GRAINE) \
	    --spring.main.banner-mode=off --logging.level.root=WARN \
	    --spring.data.mongodb.port=27051 --spring.data.mongodb.database=j_$$s > /dev/null || exit 1; \
	  echo ""; echo "  == $$s : mongosh contre Java"; \
	  docker exec -i optimisation-mongo mongosh --quiet --eval "const A = \"$$s\", B = \"j_$$s\"" \
	    --file /dev/stdin < tools/comparer-chargements.js; r=$$?; \
	  docker exec optimisation-mongo mongosh --quiet j_$$s --eval 'db.dropDatabase()' > /dev/null; \
	  [ $$r -eq 0 ] || exit 1; \
	done

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
