package github.aeonbtc.ibiswallet.ui.localization

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.util.ElectrumSeedUtil

@StringRes
fun AddressType.titleRes(): Int =
    when (this) {
        AddressType.LEGACY -> R.string.wallet_address_type_legacy_title
        AddressType.SEGWIT -> R.string.wallet_address_type_segwit_title
        AddressType.TAPROOT -> R.string.wallet_address_type_taproot_title
    }

@StringRes
fun AddressType.descriptionRes(): Int =
    when (this) {
        AddressType.LEGACY -> R.string.wallet_address_type_legacy_description
        AddressType.SEGWIT -> R.string.wallet_address_type_segwit_description
        AddressType.TAPROOT -> R.string.wallet_address_type_taproot_description
    }

fun AddressType.localizedTitle(context: Context): String = context.getString(titleRes())

fun AddressType.localizedDescription(context: Context): String = context.getString(descriptionRes())

@Composable
fun AddressType.titleText(): String = stringResource(titleRes())

@Composable
fun AddressType.descriptionText(): String = stringResource(descriptionRes())

@Composable
fun ElectrumSeedUtil.ElectrumSeedType.seedVariantLabel(): String =
    stringResource(
        when (this) {
            ElectrumSeedUtil.ElectrumSeedType.STANDARD -> R.string.wallet_electrum_seed_variant_standard
            ElectrumSeedUtil.ElectrumSeedType.SEGWIT -> R.string.wallet_electrum_seed_variant_segwit
        },
    )
