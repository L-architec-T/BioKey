package org.pcbu.libreclient.ui.util

import java.io.IOException
import org.pcbu.libreclient.crypto.CryptoException
import org.pcbu.libreclient.pairing.MalformedPairingQrException
import org.pcbu.libreclient.pairing.PairingError
import org.pcbu.libreclient.unlock.UnlockSessionError

/**
 * French, user-facing reason for a failure shown in a status line. The core/system exception
 * messages stay English for the logs; they are never shown raw (the malformed-QR one would even
 * echo the pairing key contained in the QR).
 */
fun Throwable.toUserMessage(): String = when (this) {
    is MalformedPairingQrException -> "QR code non reconnu (ce n'est pas un QR d'appairage BioKey)"
    is PairingError.Rejected -> "le PC a refusé l'appairage ($errMsg)"
    is PairingError.Network, is UnlockSessionError.Network, is IOException -> "PC injoignable sur le réseau"
    is PairingError.Crypto, is UnlockSessionError.Crypto, is CryptoException -> "échec du chiffrement (clé invalide ?)"
    is PairingError.Malformed, is UnlockSessionError.Malformed -> "réponse du PC invalide"
    else -> "erreur inattendue"
}
