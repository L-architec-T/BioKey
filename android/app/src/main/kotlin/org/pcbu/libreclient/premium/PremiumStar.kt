package org.pcbu.libreclient.premium

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pcbu.libreclient.R
import org.pcbu.libreclient.ui.theme.Accent

/** Marks a Premium feature next to its title, shown whether or not Premium is owned, so a
 * Premium user can see what they're getting and a free user what's locked. */
@Composable
fun PremiumStar(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.Star,
        contentDescription = stringResource(R.string.premium_badge),
        tint = Accent,
        modifier = modifier.padding(start = 6.dp).size(16.dp),
    )
}
