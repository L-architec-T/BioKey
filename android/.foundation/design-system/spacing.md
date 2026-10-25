# Espacements

> Échelle mesurée sur DFlow (fréquence d'usage réelle) et figée ici comme standard commun.

Grille de base **4 dp**. Utiliser uniquement ces valeurs :

| Token (usage) | Valeur | Emploi typique |
|---|---|---|
| `xxs` | 2.dp | séparation icône/texte serrée, hairlines |
| `xs` | 4.dp | padding interne de chips, espacement vertical minimal |
| `sm` | 6.dp | espacement entre éléments d'une ligne |
| `md` | 8.dp | espacement par défaut entre éléments (`Arrangement.spacedBy(8.dp)`) |
| `lg` | 12.dp | padding interne des cartes compactes |
| `xl` | 16.dp | **marge horizontale d'écran**, padding standard de `GlassCard`, inset du tray de navigation |
| `2xl` | 24.dp | séparation entre sections |

Règles :
- Marge latérale des écrans : `16.dp`. Le tray de navigation flottant : `start/end 16.dp, top 4.dp, bottom 8.dp`.
- Pas de valeurs « magiques » hors échelle (ex. 13.dp, 17.dp) sauf dimension de composant (icône 24/40.dp, logo 40.dp, hauteur de ligne 56–72.dp).
- Les tailles d'icône suivent Material : 18/20/24.dp ; slot logo fournisseur : 40.dp.
- Les rayons sont définis dans [shapes.md](shapes.md), pas ici.
