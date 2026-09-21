// =====================================================================
//  Les deux chargements rendent-ils la MEME base ?
// =====================================================================
//  La preuve du portage en Java des trois charger.js : on charge une
//  base avec mongosh (MongoDB_Optimisation), la meme avec le Java sous
//  un autre nom, et on compare TOUT — chaque collection, chaque document,
//  dans l'ordre des _id, en EJSON CANONIQUE. Le canonique garde le type
//  de chaque nombre ($numberInt / $numberDouble) et l'ordre des champs :
//  une tonne ecrite 150 en int d'un cote et 150.0 en double de l'autre
//  est une difference, et elle doit tomber.
//
//  Joue par : make comparer   (voir le Makefile)
//  Appele avec  --eval 'const A = "dune", B = "j_dune"'
// =====================================================================

const a = db.getSiblingDB(A), b = db.getSiblingDB(B);
const ca = a.getCollectionNames().sort(), cb = b.getCollectionNames().sort();
let ko = 0;
if (JSON.stringify(ca) !== JSON.stringify(cb)) {
  print("  KO  collections : " + ca.join(",") + "  <>  " + cb.join(","));
  ko++;
}
for (const c of ca) {
  if (cb.indexOf(c) < 0) continue;
  const ia = a.getCollection(c).find().sort({ _id: 1 });
  const ib = b.getCollection(c).find().sort({ _id: 1 });
  let nb = 0, diff = 0;
  while (ia.hasNext() || ib.hasNext()) {
    const da = ia.hasNext() ? EJSON.stringify(ia.next(), { relaxed: false }) : "(rien)";
    const db2 = ib.hasNext() ? EJSON.stringify(ib.next(), { relaxed: false }) : "(rien)";
    nb++;
    if (da === db2) continue;
    if (diff++ < 3) {
      print("  KO  " + c + " document " + nb);
      print("        " + A.padEnd(8) + " " + da);
      print("        " + B.padEnd(8) + " " + db2);
    }
  }
  if (diff) ko++;
  print("  " + (diff ? "KO  " : "ok  ") + c.padEnd(22) + String(nb).padStart(8)
      + " documents" + (diff ? ",  " + diff + " differents" : ", identiques"));
}
print(ko ? "  " + A + " et " + B + " DIFFERENT." : "  " + A + " et " + B + " : la meme base, type compris.");
if (ko) quit(1);
