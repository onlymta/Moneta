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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
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
        "restaurant" -> Icons.Filled.Restaurant
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
        "local_cafe" -> Icons.Filled.LocalCafe
        "grocery" -> Icons.Filled.LocalGroceryStore
        "pets" -> Icons.Filled.Pets
        "fitness" -> Icons.Filled.FitnessCenter
        "games" -> Icons.Filled.SportsEsports
        "phone" -> Icons.Filled.PhoneAndroid
        "wifi" -> Icons.Filled.Wifi
        "repairs" -> Icons.Filled.Build
        "kids" -> Icons.Filled.ChildCare
        "spa" -> Icons.Filled.Spa
        "music" -> Icons.Filled.MusicNote
        "books" -> Icons.Filled.Book
        "pharmacy" -> Icons.Filled.LocalPharmacy
        "bus" -> Icons.Filled.DirectionsBus
        "clothes" -> Icons.Filled.Checkroom
        "cake" -> Icons.Filled.Cake
        "charity" -> Icons.Filled.VolunteerActivism
        "laundry" -> Icons.Filled.LocalLaundryService
        "hotel" -> Icons.Filled.Hotel
        "star" -> Icons.Filled.Star
        "favorite" -> Icons.Filled.Favorite
        else -> Icons.Filled.Category
    }

    /** Icons offered in the picker when creating or editing a category. */
    val pickerNames: List<String> = listOf(
        "restaurant",
        "local_cafe",
        "grocery",
        "shopping_cart",
        "shopping_bag",
        "clothes",
        "directions_car",
        "local_gas_station",
        "bus",
        "flight",
        "home",
        "bolt",
        "wifi",
        "phone",
        "receipt_long",
        "repairs",
        "local_hospital",
        "pharmacy",
        "fitness",
        "spa",
        "school",
        "books",
        "kids",
        "pets",
        "movie",
        "games",
        "music",
        "subscriptions",
        "hotel",
        "cake",
        "card_giftcard",
        "charity",
        "laundry",
        "work",
        "laptop",
        "store",
        "payments",
        "savings",
        "trending_up",
        "redeem",
        "undo",
        "account_balance",
        "credit_card",
        "person",
        "groups",
        "shield",
        "flag",
        "star",
        "favorite",
        "category",
    )
}
