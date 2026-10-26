## BioKey 0.2.4

### Téléphone (Android)
- **Correction** : avec le chiffrement biométrique activé, le bouton « Approuver » de la notification (option « Notification montre connectée ») validait sans empreinte et le PC affichait une erreur. Pour un PC protégé par empreinte, la notification ouvre désormais toujours la demande d'empreinte.
- Le déverrouillage direct ouvre de nouveau la demande d'empreinte immédiatement (la 0.2.3 l'avait coupé à tort).
- Si la clé protégée par empreinte a été invalidée (empreinte ajoutée ou modifiée), le réappairage en crée une nouvelle au lieu de réutiliser l'ancienne.

### PC (Windows, Linux, macOS)
- Le module de service (connexion, sudo, verrouillage) est réinstallé automatiquement après chaque mise à jour. Les modules installés par l'ancienne version 3.3.4 de PC Bio Unlock n'étaient jamais remplacés.
- Test de déverrouillage : les messages longs passent à la ligne au lieu d'être coupés.
