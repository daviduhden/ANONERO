/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2023-2026 r4v3r23
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.anonero.R
import io.anonero.ui.theme.Black
import io.anonero.ui.theme.DarkGray
import io.anonero.ui.theme.DarkOrange
import io.anonero.ui.theme.Gray
import io.anonero.ui.theme.Orange
import io.anonero.ui.theme.ThemeMode
import io.anonero.ui.theme.Typography
import io.anonero.ui.theme.White

private val LightColorScheme = lightColorScheme(
    primary = DarkOrange,
    onPrimary = White,
    primaryContainer = Orange,
    onPrimaryContainer = Black,
    secondary = Gray,
    onSecondary = White,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    error = io.anonero.ui.theme.DangerColor,
)

private val DarkColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Black,
    primaryContainer = DarkOrange,
    onPrimaryContainer = White,
    secondary = DarkGray,
    onSecondary = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White
)

val robotoMonoFamily = FontFamily(
    Font(R.font.robotomono_light, FontWeight.Light),
    Font(R.font.robotomono_regular, FontWeight.Normal),
    Font(R.font.robotomono_medium, FontWeight.Medium),
    Font(R.font.robotomono_bold, FontWeight.Bold),
    Font(R.font.robotomono_semi_bold, FontWeight.SemiBold),
)


@Composable
fun AnonNeroTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColorScheme else LightColorScheme,
        content = content,
        typography = Typography.copy(
            titleLarge = Typography.titleLarge.copy(fontFamily = robotoMonoFamily),
            titleSmall = Typography.titleSmall.copy(fontFamily = robotoMonoFamily),
            titleMedium = Typography.titleMedium.copy(fontFamily = robotoMonoFamily),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = robotoMonoFamily),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = robotoMonoFamily),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = robotoMonoFamily),
            bodyLarge = Typography.bodyLarge.copy(fontFamily = robotoMonoFamily),
            bodySmall = Typography.bodySmall.copy(fontFamily = robotoMonoFamily),
            bodyMedium = Typography.bodyMedium.copy(fontFamily = robotoMonoFamily),
            displayLarge = Typography.displayLarge.copy(fontFamily = robotoMonoFamily),
            displaySmall = Typography.displaySmall.copy(fontFamily = robotoMonoFamily),
            displayMedium = Typography.displayMedium.copy(fontFamily = robotoMonoFamily),
            labelLarge = Typography.labelLarge.copy(fontFamily = robotoMonoFamily),
            labelSmall = Typography.labelSmall.copy(fontFamily = robotoMonoFamily),
            labelMedium = Typography.labelMedium.copy(fontFamily = robotoMonoFamily),
        )
    )
}

@Composable
fun AnonOutlineButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    child: @Composable () -> Unit
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(16.dp)
    ) {
        child()
    }
}