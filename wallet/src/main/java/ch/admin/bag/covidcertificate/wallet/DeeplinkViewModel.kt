/*
 * Copyright (c) 2021 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package ch.admin.bag.covidcertificate.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ch.admin.bag.covidcertificate.sdk.android.CovidCertificateSdk
import ch.admin.bag.covidcertificate.sdk.core.models.state.DecodeState

class DeeplinkViewModel(application: Application) : AndroidViewModel(application) {

	private val deeplinkImportMutableLiveData: MutableLiveData<DecodeState?> = MutableLiveData()
	val deeplinkImportLiveData: LiveData<DecodeState?> = deeplinkImportMutableLiveData

	fun importDeeplink(path: String) {
		deeplinkImportMutableLiveData.postValue(CovidCertificateSdk.Wallet.decode(path))
	}

	fun clearDeeplink() {
		deeplinkImportMutableLiveData.value = null
	}
}