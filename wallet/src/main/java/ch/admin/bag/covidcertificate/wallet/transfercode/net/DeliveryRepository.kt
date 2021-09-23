/*
 * Copyright (c) 2021 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package ch.admin.bag.covidcertificate.wallet.transfercode.net

import android.content.Context
import ch.admin.bag.covidcertificate.common.BuildConfig
import ch.admin.bag.covidcertificate.common.util.HttpIOException
import ch.admin.bag.covidcertificate.sdk.android.CovidCertificateSdk
import ch.admin.bag.covidcertificate.sdk.android.data.Config
import ch.admin.bag.covidcertificate.sdk.android.net.CertificatePinning
import ch.admin.bag.covidcertificate.sdk.android.net.interceptor.JwsInterceptor
import ch.admin.bag.covidcertificate.sdk.android.net.interceptor.UserAgentInterceptor
import ch.admin.bag.covidcertificate.sdk.android.utils.SingletonHolder
import ch.admin.bag.covidcertificate.sdk.core.extensions.toBase64
import ch.admin.bag.covidcertificate.wallet.transfercode.logic.TransferCodeCrypto
import ch.admin.bag.covidcertificate.wallet.transfercode.model.ConvertedCertificate
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.KeyPair

internal class DeliveryRepository private constructor(deliverySpec: DeliverySpec) {

	companion object : SingletonHolder<DeliveryRepository, DeliverySpec>(::DeliveryRepository) {
		private const val KEY_PAIR_ALGORITHM = "RSA2048"
	}

	private val deliveryService: DeliveryService

	init {
		val rootCa = CovidCertificateSdk.getRootCa(deliverySpec.context)
		val expectedCommonName = CovidCertificateSdk.getExpectedCommonName()
		val okHttpBuilder = OkHttpClient.Builder()
			.certificatePinner(CertificatePinning.pinner)
			.addInterceptor(JwsInterceptor(rootCa, expectedCommonName))
			.addInterceptor(UserAgentInterceptor(Config.userAgent))

		val cacheSize = 5 * 1024 * 1024 // 5 MB
		val cache = Cache(deliverySpec.context.cacheDir, cacheSize.toLong())
		okHttpBuilder.cache(cache)

		if (BuildConfig.DEBUG) {
			val httpInterceptor = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
			okHttpBuilder.addInterceptor(httpInterceptor)
		}

		deliveryService = Retrofit.Builder()
			.baseUrl(deliverySpec.baseUrl)
			.client(okHttpBuilder.build())
			.addConverterFactory(MoshiConverterFactory.create())
			.build()
			.create(DeliveryService::class.java)
	}

	suspend fun register(transferCode: String, keyPair: KeyPair): Boolean {
		val signaturePayload = TransferCodeCrypto.buildMessage("register", transferCode)
		val signature = TransferCodeCrypto.sign(keyPair, signaturePayload) ?: return false
		val deliveryRegistration = DeliveryRegistration(
			transferCode,
			keyPair.public.encoded.toBase64(),
			KEY_PAIR_ALGORITHM,
			signaturePayload,
			signature
		)

		val response = deliveryService.register(deliveryRegistration)
		return response.isSuccessful
	}

	@Throws(HttpIOException::class)
	suspend fun download(transferCode: String, keyPair: KeyPair): List<ConvertedCertificate> {
		val signaturePayload = TransferCodeCrypto.buildMessage("get", transferCode)
		val signature = TransferCodeCrypto.sign(keyPair, signaturePayload) ?: return emptyList()
		val requestDeliveryPayload = RequestDeliveryPayload(transferCode, signaturePayload, signature)

		val response = deliveryService.get(requestDeliveryPayload)
		if (response.code() == 404) {
			// A 404 indicates that the transfer code was not found on the server, so it was either already delivered or is expired
			return emptyList()
		} else if (!response.isSuccessful) {
			// Any other non-successful status code is considered an error and should be handled by the caller in a try-catch
			throw HttpIOException(response)
		}

		val covidCertDelivery = response.body() ?: return emptyList()
		if (covidCertDelivery.covidCerts.isEmpty()) {
			return emptyList()
		}

		return covidCertDelivery.covidCerts.mapNotNull {
			val hcert = TransferCodeCrypto.decrypt(keyPair, it.encryptedHcert)
			val pdf = TransferCodeCrypto.decrypt(keyPair, it.encryptedPdf)
			if (hcert != null && pdf != null) {
				ConvertedCertificate(hcert, pdf)
			} else {
				null
			}
		}
	}

	suspend fun complete(transferCode: String, keyPair: KeyPair): Boolean {
		val signaturePayload = TransferCodeCrypto.buildMessage("delete", transferCode)
		val signature = TransferCodeCrypto.sign(keyPair, signaturePayload) ?: return false
		val requestDeliveryPayload = RequestDeliveryPayload(transferCode, signaturePayload, signature)

		val response = deliveryService.complete(requestDeliveryPayload)
		return response.isSuccessful
	}

}

class DeliverySpec(val context: Context, val baseUrl: String)