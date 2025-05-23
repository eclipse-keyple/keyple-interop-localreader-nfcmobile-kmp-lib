/* **************************************************************************************
 * Copyright (c) 2025 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.interop.localreader.nfcmobile.api

import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import org.eclipse.keyple.interop.jsonapi.client.api.ReaderIOException
import org.eclipse.keyple.interop.jsonapi.client.spi.LocalReader

/**
 * An implementation of Keyple-less LocalReader that provides NFC reading capability for Android,
 * iOS and PCSC readers on Windows/Mac/Linux. For exotic NFC reader hardware, or advanced
 * fine-tuning, you can provide your own implementation of the LocalReader interface to the
 * keyple-less-distributed lib.
 */
class MultiplatformNfcReader(private val nfcReader: LocalNfcReader) : LocalReader {

  private companion object {
    private const val TAG = "NFCReader"
  }

  private val mutex = Mutex()

  override fun getName(): String {
    return nfcReader.name
  }

  override fun setScanMessage(message: String) {
    nfcReader.scanMessage = message
  }

  override suspend fun waitForCardPresent(): Boolean {
    if (mutex.isLocked) {
      throw ReaderIOException("Reader is already in use")
    }
    try {
      mutex.lock()
      return nfcReader.waitForCardPresent()
    } finally {
      mutex.unlock()
    }
  }

  override fun startCardDetection(onCardDetected: () -> Unit) {
    try {
      if (!mutex.tryLock()) {
        throw ReaderIOException("Reader is already in use")
      }
      nfcReader.startCardDetection {
        Napier.d(tag = TAG, message = "Card found")
        mutex.unlock()
        onCardDetected()
      }
    } catch (e: Exception) {
      mutex.unlock()
      throw e
    }
  }

  override fun openPhysicalChannel() {
    if (!mutex.tryLock()) {
      throw ReaderIOException("Reader is already in use")
    }
    try {
      nfcReader.openPhysicalChannel()
    } finally {
      mutex.unlock()
    }
  }

  override fun closePhysicalChannel() {
    nfcReader.closePhysicalChannel()
  }

  override fun getPowerOnData(): String {
    return nfcReader.getPowerOnData()
  }

  override fun transmitApdu(commandApdu: ByteArray): ByteArray {
    if (!mutex.tryLock()) {
      throw ReaderIOException("Reader is already in use")
    }
    try {
      return nfcReader.transmitApdu(commandApdu)
    } finally {
      mutex.unlock()
    }
  }

  override fun release() {
    nfcReader.releaseReader()
    if (mutex.isLocked) {
      mutex.unlock()
    }
  }
}
