# =====================================================================
#  Le Makefile, pour Windows : sans make, sans WSL, sans Git Bash.
#  On ne l'appelle pas directement : make.cmd, a la racine, le lance.
#
#      .\make bases                comme   make bases
#      .\make bases VOLUME=10      comme   make bases VOLUME=10
#      .\make dune
#
# Chaque cible fait ce que fait celle du Makefile : les memes commandes
# docker, java et bru. Maven passe par mvnw.cmd au lieu de ./mvnw.
# Une cible qui change dans le Makefile change ici aussi.
#
# Ecrit pour Windows PowerShell 5.1 (celui de Windows 10 et 11) :
#   - ce fichier reste en ASCII pur : 5.1 lit un .ps1 sans BOM comme de
#     l'ANSI, et un accent y deviendrait du charabia ;
#   - aucun guillemet double dans un argument passe a docker : 5.1 les
#     avale en route. Le JavaScript de --eval n'utilise que des simples.
# =====================================================================

$Racine    = Split-Path -Parent $PSScriptRoot
$Compose   = @('compose', '-f', (Join-Path $Racine 'docker/docker-compose.yml'))
$Conteneur = 'connexion-mongo'
$Version   = '1.0.0'
$Sujets    = @('dune', 'tortues', 'bateaux')
# mvnw.cmd sous Windows ; mvnw ailleurs (pwsh sous Linux ou macOS).
$Mvnw      = Join-Path $Racine $(if ($env:OS -eq 'Windows_NT') { 'mvnw.cmd' } else { 'mvnw' })

# VOLUME, GRAINE, OPTIMISATION : memes valeurs par defaut que le Makefile.
$Reglages = @{ VOLUME = '1'; GRAINE = '20260920'; OPTIMISATION = (Join-Path (Split-Path -Parent $Racine) 'MongoDB_Optimisation') }

# Pas nommee Docker : PowerShell ne distingue pas les majuscules, et
# & docker se rappellerait lui-meme.
function Lancer {
    & docker @args
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Un programme autre que docker (java, mvnw.cmd, bru) : meme arret sur erreur.
function Executer([string]$Programme) {
    & $Programme @args
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Jar([string]$Sujet) { Join-Path $Racine "target/connexion-complete-$Version-$Sujet.jar" }

function Aide {
    @'

  Mise en route, dans cet ordre
    .\make bases             - demarre MongoDB (27061), compile, et fabrique
                               les trois bases. VOLUME=10 pour dix fois plus.
    .\make dune              - lance l application << dune >>     (8081)
    .\make tortues           - lance l application << tortues >>  (8082)
    .\make bateaux           - lance l application << bateaux >>  (8083)

  Les trois applications sont independantes : une base chacune,
  un port chacune. On peut les lancer toutes les trois a la fois,
  mais PAS mesurer sur deux en meme temps : le banc lit des
  compteurs de SERVEUR, et deux mesures simultanees se melangent.

  Une base seule
    .\make charger-dune      - la jette et la refait (tortues, bateaux)

  Les tester
    .\make bruno-dune        - joue la collection Bruno du sujet
    .\make bruno             - les trois, l une apres l autre
                               (demande les trois applications lancees)
    .\make comparer          - le chargement Java rend-il la base du mongosh ?
                               (demande ..\MongoDB_Optimisation, demarre et charge)

    .\make construire        - compile et fabrique les trois jars
    .\make etat              - MongoDB repond-il, et sur quelles bases
    .\make arreter           - arrete MongoDB, conserve les donnees
    .\make purger            - arrete et EFFACE le volume
    .\make propre            - efface target/

  Les endpoints, pour chaque sujet <s> = dune | tortues | bateaux :
    GET  /api/<s>/etat              dans quel etat est la base
    GET  /api/<s>/mesurer           LE BANC : les quatre requetes
    GET  /api/<s>/requetes/R1       une requete, avec ses lignes
    POST /api/<s>/optimiser         LA CORRECTION (ecrit)
    POST /api/<s>/remettre          tout defaire (ecrit)
    GET  /api/<s>/index             les index poses
    GET  /api/<s>/exploration       l exercice d exploration

'@ | Write-Host
}

function Demarrer {
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host ''
        Write-Host '  Docker ne repond pas. Lancer Docker Desktop, attendre que'
        Write-Host '  la baleine arrete de clignoter, et recommencer.'
        Write-Host ''
        exit 1
    }
    Lancer @Compose up -d
    Write-Host '  attente du serveur...'
    for ($i = 0; $i -lt 60; $i++) {
        & docker exec $Conteneur mongosh --quiet --eval 'db.runCommand({ping:1})' *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    }
}

function Charger([string]$Sujet) {
    Executer java -jar (Jar $Sujet) --charger "--volume=$($Reglages.VOLUME)" "--graine=$($Reglages.GRAINE)" `
        --spring.main.banner-mode=off --logging.level.root=WARN
}

function Construire {
    Push-Location $Racine
    try { Executer $Mvnw -q package -DskipTests }
    finally { Pop-Location }
    Get-ChildItem (Join-Path $Racine "target/connexion-complete-$Version-*.jar") | ForEach-Object { Write-Host "target/$($_.Name)" }
}

# Le script du Makefile, avec des guillemets simples partout.
$EtatJs = "print('  MongoDB     ' + db.version()); ['dune','tortues','bateaux'].forEach(function(b){ var d = db.getSiblingDB(b); var n = d.getCollectionNames().length; var ix = d.getCollectionNames().reduce(function(s,c){ return s + d.getCollection(c).getIndexes().filter(function(i){return i.name!=='_id_';}).length; }, 0); print('  ' + b.padEnd(12) + n + ' collections, ' + ix + ' index hors _id'); })"

function Etat {
    & docker exec $Conteneur mongosh --quiet --eval $EtatJs 2>$null
    if ($LASTEXITCODE -ne 0) { Write-Host '  MongoDB     ARRETE   (.\make bases)' }
}

# La preuve du portage des chargements : voir la cible comparer du Makefile.
# Le Makefile passe tools/comparer-chargements.js sur l'entree standard ;
# ici, il est copie dans le conteneur, puis joue par --file.
function Comparer {
    Construire
    $opt = $Reglages.OPTIMISATION
    if (-not (Test-Path (Join-Path $opt 'make.cmd'))) {
        Write-Host "  .\make comparer demande $opt   (OPTIMISATION=C:\chemin)"
        exit 1
    }
    Executer (Join-Path $opt 'make.cmd') demarrer tout "VOLUME=$($Reglages.VOLUME)" "GRAINE=$($Reglages.GRAINE)"
    Lancer cp (Join-Path $Racine 'tools/comparer-chargements.js') 'optimisation-mongo:/tmp/comparer-chargements.js'
    foreach ($s in $Sujets) {
        & java -jar (Jar $s) --charger "--volume=$($Reglages.VOLUME)" "--graine=$($Reglages.GRAINE)" `
            --spring.main.banner-mode=off --logging.level.root=WARN `
            --spring.data.mongodb.port=27051 "--spring.data.mongodb.database=j_$s" > $null
        if ($LASTEXITCODE -ne 0) { exit 1 }
        Write-Host ''; Write-Host "  == $s : mongosh contre Java"
        & docker exec optimisation-mongo mongosh --quiet --eval "const A = '$s', B = 'j_$s'" --file /tmp/comparer-chargements.js
        $r = $LASTEXITCODE
        & docker exec optimisation-mongo mongosh --quiet "j_$s" --eval 'db.dropDatabase()' > $null
        if ($r -ne 0) { exit 1 }
    }
}

# Les collections Bruno. bru vient de @usebruno/cli.
function Bruno([string]$Sujet) {
    Push-Location (Join-Path $Racine "bruno/$Sujet")
    try { Executer bru run --env local }
    finally { Pop-Location }
}

function Cible([string]$c) {
    switch -regex ($c) {
        '^aide$'       { Aide; return }
        '^demarrer$'   { Demarrer; return }
        '^arreter$'    { Lancer @Compose down; return }
        '^purger$'     { Lancer @Compose down -v; return }
        '^charger-(dune|tortues|bateaux)$' { Charger $Matches[1]; return }
        '^bases$'      {
            Demarrer; Construire
            foreach ($s in $Sujets) { Charger $s }
            Write-Host ''
            Write-Host '  Trois bases chargees sur mongodb://localhost:27061 : dune, tortues, bateaux.'
            Write-Host '  Enchainer :   .\make dune'
            return
        }
        '^etat$'       { Etat; return }
        '^comparer$'   { Comparer; return }
        '^construire$' { Construire; return }
        '^(dune|tortues|bateaux)$' { Executer java -jar (Jar $c); return }
        '^bruno-(dune|tortues|bateaux)$' { Bruno $Matches[1]; return }
        '^bruno$'      { foreach ($s in $Sujets) { Bruno $s }; return }
        '^propre$'     {
            Push-Location $Racine
            try { Executer $Mvnw -q clean }
            finally { Pop-Location }
            return
        }
    }
    Write-Host "  Cible inconnue : $c   (.\make aide liste tout)"
    exit 2
}

# Comme make : les NOM=valeur reglent, le reste sont des cibles jouees
# dans l'ordre. Sans cible, l'aide.
$Cibles = @()
foreach ($a in $args) {
    if ($a -match '^([A-Za-z]+)=(.*)$') {
        # Garder les deux morceaux AVANT le test suivant : un -match reussi
        # remplace $Matches, et VOLUME=10 aurait charge VOLUME vide.
        $nom = $Matches[1].ToUpper()
        $val = $Matches[2]
        if (-not $Reglages.ContainsKey($nom)) { Write-Host "  Reglage inconnu : $nom (VOLUME, GRAINE ou OPTIMISATION)"; exit 2 }
        if ($nom -ne 'OPTIMISATION' -and $val -notmatch '^\d+$') { Write-Host "  $nom doit etre un nombre entier : $val"; exit 2 }
        $Reglages[$nom] = $val
    } else {
        $Cibles += $a
    }
}
if ($Cibles.Count -eq 0) { $Cibles = @('aide') }
foreach ($c in $Cibles) { Cible $c }
