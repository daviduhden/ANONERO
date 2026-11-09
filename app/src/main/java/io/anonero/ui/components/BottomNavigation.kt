package io.anonero.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.anonero.ui.home.BottomNavigationItem
import io.anonero.ui.home.getRoutes

val BottomNavHeight = 44.dp

@Composable
fun MainBottomNavigation(
    currentRoute: Any,
    onTabSelected: (BottomNavigationItem) -> Unit
) {

    var selectedItem by remember { mutableIntStateOf(0) }
    LaunchedEffect(key1 = currentRoute) {
        getRoutes().forEachIndexed { index, bottomNavigationItem ->
            val isSelected = if (currentRoute is String) {
                currentRoute.contains(bottomNavigationItem.getRouteAsString)
            } else {
                currentRoute == bottomNavigationItem.getRouteAsString
            }
            if (isSelected) {
                selectedItem = index
            }
        }
    }
    val navigationItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(
            alpha = 0.2f
        ),
        selectedTextColor = MaterialTheme.colorScheme.primary,
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        getRoutes().forEachIndexed { item, bottomNavigationItem ->
            val isSelected = selectedItem == item
            NavigationBarItem(
                selected = isSelected,
                colors = navigationItemColors,
                onClick = {
                    if (!isSelected) {
                        onTabSelected(bottomNavigationItem)
                    }
                },
                icon = {
                    Icon(
                        bottomNavigationItem.icon,
                        contentDescription = bottomNavigationItem.title
                    )
                },
            )
        }
    }
}
