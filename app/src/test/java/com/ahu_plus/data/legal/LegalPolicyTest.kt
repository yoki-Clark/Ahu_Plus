package com.ahu_plus.data.legal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalPolicyTest {
    @Test
    fun `current acceptance satisfies the startup gate`() {
        assertTrue(
            LegalConsentRepository.isCurrent(
                LegalAcceptance(
                    privacyVersion = LegalDocumentVersions.PRIVACY,
                    disclaimerVersion = LegalDocumentVersions.DISCLAIMER,
                    acceptedAtEpochMillis = 1L,
                    acceptedAppVersion = "test",
                )
            )
        )
    }

    @Test
    fun `older or incomplete acceptance requires consent again`() {
        assertFalse(
            LegalConsentRepository.isCurrent(
                LegalAcceptance(
                    privacyVersion = LegalDocumentVersions.PRIVACY - 1,
                    disclaimerVersion = LegalDocumentVersions.DISCLAIMER,
                    acceptedAtEpochMillis = 1L,
                    acceptedAppVersion = "test",
                )
            )
        )
        assertFalse(
            LegalConsentRepository.isCurrent(
                LegalAcceptance(
                    privacyVersion = LegalDocumentVersions.PRIVACY,
                    disclaimerVersion = LegalDocumentVersions.DISCLAIMER,
                    acceptedAtEpochMillis = 0L,
                    acceptedAppVersion = "test",
                )
            )
        )
    }

    @Test
    fun `bundled legal documents do not disclose a removed OCR service`() {
        val allText = LegalDocumentKind.entries.joinToString("\n") { kind ->
            LegalContent.document(kind).toString()
        }.lowercase()

        assertFalse(allText.contains("openahu"))
        assertTrue(allText.contains("手动输入"))
        assertTrue(allText.contains("adwmh.ahu.edu.cn"))
    }
}
