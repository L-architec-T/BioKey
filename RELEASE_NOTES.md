## BioKey 0.2.8

### Téléphone (Android)
- **Déverrouillage direct** : la demande d'empreinte peut s'afficher sans toucher la notification.
  - Si Android bloque les notifications plein écran, les réglages le signalent avec un bouton **Autoriser** (écran éteint ou verrouillé).
  - Nouvelle permission facultative « Superposition sur d'autres applis » : l'empreinte s'ouvre aussi pendant que vous utilisez le téléphone.
- **Chiffrement biométrique** : le réglage s'applique maintenant aux PC déjà appairés, sans réappairage.
  - Activé : la clé est chiffrée tout de suite.
  - Désactivé : chaque PC repasse sans chiffrement à son prochain déverrouillage.
  - L'empreinte reste demandée pour confirmer chaque déverrouillage, sauf avec le déverrouillage automatique.

### PC
- Après une mise à jour, BioKey redémarre bien tout seul et met à jour ses modules d'un coup, sans avoir à le rouvrir. Ce correctif agit à partir de la mise à jour suivante.
