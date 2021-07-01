/*
 * Copyright (c) 2021 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package ch.admin.bag.covidcertificate.wallet.light

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ch.admin.bag.covidcertificate.common.util.makeSubStringBold
import ch.admin.bag.covidcertificate.sdk.core.data.ErrorCodes
import ch.admin.bag.covidcertificate.sdk.core.models.healthcert.DccHolder
import ch.admin.bag.covidcertificate.wallet.R
import ch.admin.bag.covidcertificate.wallet.databinding.FragmentCertificateLightConversionBinding
import ch.admin.bag.covidcertificate.wallet.light.model.CertificateLightConversionState

class CertificateLightConversionFragment : Fragment(R.layout.fragment_certificate_light_conversion) {

	companion object {
		private const val ARG_DCC_HOLDER = "ARG_DCC_HOLDER"

		fun newInstance(dccHolder: DccHolder) = CertificateLightConversionFragment().apply {
			arguments = bundleOf(ARG_DCC_HOLDER to dccHolder)
		}
	}

	private val viewModel by viewModels<CertificateLightViewModel>()
	private var _binding: FragmentCertificateLightConversionBinding? = null
	private val binding get() = _binding!!

	private lateinit var dccHolder: DccHolder

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		dccHolder = (arguments?.getSerializable(ARG_DCC_HOLDER) as? DccHolder)
			?: throw IllegalStateException("Certificate light fragment created without a DccHolder!")
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentCertificateLightConversionBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		viewModel.conversionState.observe(viewLifecycleOwner) { onConversionStateChanged(it) }

		binding.certificateLightConversionActivateButton.setOnClickListener { viewModel.convert(dccHolder) }
		binding.certificateLightConversionRetryButton.setOnClickListener { viewModel.convert(dccHolder) }
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	@SuppressLint("SetTextI18n")
	private fun onConversionStateChanged(state: CertificateLightConversionState) {
		when (state) {
			is CertificateLightConversionState.LOADING -> {
				binding.certificateLightConversionLoadingIndicator.isVisible = true
				binding.certificateLightConversionContent.isVisible = false
			}
			is CertificateLightConversionState.SUCCESS -> {
				binding.certificateLightConversionLoadingIndicator.isVisible = false
				binding.certificateLightConversionContent.isVisible = true
				binding.certificateLightConversionIntroLayout.isVisible = true
				binding.certificateLightConversionErrorLayout.isVisible = false

				// TODO Forward to certificate light detail fragment and pop back to home
			}
			is CertificateLightConversionState.ERROR -> {
				binding.certificateLightConversionLoadingIndicator.isVisible = false
				binding.certificateLightConversionContent.isVisible = true
				binding.certificateLightConversionIntroLayout.isVisible = false
				binding.certificateLightConversionErrorLayout.isVisible = true
				binding.certificateLightConversionErrorCode.text = state.error.code

				if (state.error.code == ErrorCodes.GENERAL_OFFLINE) {
					binding.certificateLightConversionStatusIcon.setImageResource(R.drawable.ic_no_connection)
					val title = getString(R.string.wallet_certificate_light_detail_activation_network_error_title)
					val text = getString(R.string.wallet_certificate_light_detail_activation_network_error_text)
					binding.certificateLightConversionStatusText.text = "$title\n$text".makeSubStringBold(title)
				} else {
					binding.certificateLightConversionStatusIcon.setImageResource(R.drawable.ic_process_error)
					val title = getString(R.string.wallet_certificate_light_detail_activation_general_error_title)
					val text = getString(R.string.wallet_certificate_light_detail_activation_general_error_text)
					binding.certificateLightConversionStatusText.text = "$title\n$text".makeSubStringBold(title)
				}
			}
		}
	}

}