/* **************************************************************************************
 * Copyright (c) 2024 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.interop.localreader.nfcmobile

import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import org.eclipse.keyple.interop.jsonapi.client.spi.LocalReader
import org.eclipse.keyple.interop.jsonapi.client.spi.CardIOException
import org.eclipse.keyple.interop.jsonapi.client.spi.ReaderIOException

private const val TAG = "NFCReader"

/**
 * An implementation of Keyple-less LocalReader that provides NFC reading capability for Android,
 * iOS and PCSC readers on Windows/Mac/Linux. For exotic NFC reader hardware, or advanced fine
 * tuning, you can provide your own implementation of the LocalReader interface to the
 * keyple-less-distributed lib.
 */
class MultiplatformNfcReader(private val nfcReader: LocalNfcReader) : LocalReader {
  private val mutex = Mutex()

  override fun setScanMessage(msg: String) {
    nfcReader.scanMessage = msg
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

  override fun startCardDetection(onCardFound: () -> Unit) {
    try {
      if (!mutex.tryLock()) {
        throw ReaderIOException("Reader is already in use")
      }
      nfcReader.startCardDetection {
        Napier.d(tag = TAG, message = "Card found")
        mutex.unlock()
        onCardFound()
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

  override fun getName(): String {
    return nfcReader.name
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

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class LocalNfcReader {
  var scanMessage: String
  var name: String

  /**
   * Waits for a card to be inserted in the reader
   *
   * @returns True if a card is detected, otherwise false
   * @throws TimeoutException
   * @throws CancellationException
   */
  suspend fun waitForCardPresent(): Boolean

  /** Waits for a card to be inserted in the reader, then triggers the provided callback. */
  fun startCardDetection(onCardFound: () -> Unit)

  /** Stop NFC polling and release resources. */
  fun releaseReader()

  /**
   * Attempts to open the physical channel (to establish communication with the card).
   *
   * @throws ReaderIOException If an I/O error occurs while communicating with the reader.
   * @throws CardIOException If an I/O error occurs while communicating with the card.
   */
  fun openPhysicalChannel()

  /**
   * Attempts to close the current physical channel. The physical channel may have been implicitly
   * closed previously by a card withdrawal.
   */
  fun closePhysicalChannel()

  /**
   * Gets the power-on data. The power-on data is defined as the data retrieved by the reader when
   * the card is inserted.
   *
   * @return an empty String as this data is not used in mobile context where we only work with ISO
   *   cards.
   */
  fun getPowerOnData(): String

  /**
   * Transmits an Application Protocol Data Unit (APDU) command to the smart card and receives the
   * response.
   *
   * @param commandApdu: The command APDU to be transmitted.
   * @return The response APDU received from the smart card.
   * @throws ReaderIOException If an I/O error occurs while communicating with the reader.
   * @throws CardIOException If an I/O error occurs while communicating with the card.
   */
  fun transmitApdu(commandApdu: ByteArray): ByteArray
}
