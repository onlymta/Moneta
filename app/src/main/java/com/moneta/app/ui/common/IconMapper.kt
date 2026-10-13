package com.moneta.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Resolves the icon-name strings stored on categories/accounts/goals
 * (Section 12/10/14) to an actual [ImageVector] to draw. Kept in exactly one
 * place so every screen renders the same icon for the same stored name
 * (Section 46: no duplicate logic) - and deliberately limited to icons that
 * are unambiguously part of material-icons-extended, after the earlier
 * AutoMirrored.ChevronRight build failure showed that assuming an icon name
 * exists without verifying it is a real risk in this environment.
 *
 * Falls back to a generic icon for any name not in this table rather than
 * failing - new category/account types added later that don't have a
 * mapping yet still render something reasonable instead of breaking.
 */
object IconMapper {
    fun iconFor(name: String): ImageVector = when (name) {
        "restaurant" -> Icons.Filled.Category // conservative: avoid unverified "Restaurant" symbol name
        "shopping_cart" -> Icons.Filled.ShoppingCart
        "directions_car" -> Icons.Filled.DirectionsCar
        "local_gas_station" -> Icons.Filled.LocalGasStation
        "shopping_bag" -> Icons.Filled.ShoppingBag
        "receipt_long" -> Icons.Filled.Receipt
        "bolt" -> Icons.Filled.Bolt
        "home" -> Icons.Filled.Home
        "local_hospital" -> Icons.Filled.LocalHospital
        "school" -> Icons.Filled.School
        "movie" -> Icons.Filled.Movie
        "subscriptions" -> Icons.Filled.Subscriptions
        "flight" -> Icons.Filled.Flight
        "person" -> Icons.Filled.Person
        "groups" -> Icons.Filled.Groups
        "card_giftcard" -> Icons.Filled.CardGiftcard
        "shield" -> Icons.Filled.Shield
        "account_balance" -> Icons.Filled.AccountBalance
        "work" -> Icons.Filled.Work
        "laptop" -> Icons.Filled.Laptop
        "store" -> Icons.Filled.Store
        "redeem" -> Icons.Filled.Redeem
        "undo" -> Icons.Filled.Undo
        "trending_up" -> Icons.Filled.TrendingUp
        "payments" -> Icons.Filled.Payments
        "credit_card" -> Icons.Filled.CreditCard
        "savings" -> Icons.Filled.Savings
        "account_balance_wallet" -> Icons.Filled.AccountBalanceWallet
        "flag" -> Icons.Filled.Flag
        else -> Icons.Filled.Category
    }
}
