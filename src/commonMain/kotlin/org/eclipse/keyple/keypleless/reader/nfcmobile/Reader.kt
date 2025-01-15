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
package org.eclipse.keyple.keypleless.reader.nfcmobile

import kotlinx.coroutines.sync.Mutex
import org.eclipse.keyple.keypleless.distributed.client.spi.LocalReader
import org.eclipse.keyple.keypleless.distributed.client.spi.ReaderIOException

class MultiplatformReader(private val nfcReader: LocalNfcReader) : LocalReader {
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

  override suspend fun startCardDetection(onCardFound: () -> Unit) {
    if (mutex.isLocked) {
      throw ReaderIOException("Reader is already in use")
    }

    try {
      mutex.lock()
      nfcReader.startCardDetection {
        mutex.unlock()
        onCardFound()
      }
    } catch (e: Exception) {
      mutex.unlock()
      throw e
    }
  }

  override suspend fun openPhysicalChannel() {
    if (mutex.isLocked) {
      throw ReaderIOException("Reader is already in use")
    }

    try {
      mutex.lock()
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

  override fun name(): String {
    return nfcReader.name
  }

  override suspend fun transmitApdu(commandApdu: ByteArray): ByteArray {
    if (mutex.isLocked) {
      throw ReaderIOException("Reader is already in use")
    }

    try {
      mutex.lock()
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

expect class LocalNfcReader {
  var scanMessage: String
  var name: String

  /**
   * Waits for a card to be inserted in the reader
   *
   * @returns True if a card is detected, otherwise false
   * @throws TimeoutException or CancellationException
   */
  suspend fun waitForCardPresent(): Boolean

  suspend fun startCardDetection(onCardFound: () -> Unit)

  fun releaseReader()

  /**
   * Attempts to open the physical channel (to establish communication with the card). <exception
   * cref="ReaderNotFoundException">If the communication with the reader has failed.</exception>
   * <exception cref="CardIOException">If the communication with the card has failed.</exception>
   */
  fun openPhysicalChannel()

  /**
   * Attempts to close the current physical channel. The physical channel may have been implicitly
   * closed previously by a card withdrawal.
   *
   * <exception cref="ReaderNotFoundException">If the communication with the reader has
   * failed.</exception>
   */
  fun closePhysicalChannel()

  /**
   * Gets the power-on data. The power-on data is defined as the data retrieved by the reader when
   * the card is inserted.
   *
   * In the case of a contactless reader, the reader decides what this data is. Contactless readers
   * provide a virtual ATR (partially standardized by the PC/SC standard), but other devices can
   * have their own definition, including for example elements from the anti-collision stage of the
   * ISO14443 protocol (ATQA, ATQB, ATS, SAK, etc).
   *
   * These data being variable from one reader to another, they are defined here in string format
   * which can be either a hexadecimal string or any other relevant information.
   *
   * @return a non empty String
   */
  fun getPowerOnData(): String

  /**
   * Transmits an Application Protocol Data Unit (APDU) command to the smart card and receives the
   * response.
   *
   * @param commandApdu: The command APDU to be transmitted.
   * @return The response APDU received from the smart card.
   *
   * <exception cref="ReaderNotFoundException">If the communication with the reader has
   * failed.</exception> <exception cref="CardIOException">If the communication with the card has
   * failed.</exception>
   */
  fun transmitApdu(commandApdu: ByteArray): ByteArray
}
