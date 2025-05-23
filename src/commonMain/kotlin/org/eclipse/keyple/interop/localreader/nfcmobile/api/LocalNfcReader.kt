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
package org.eclipse.keyple.interop.localreader.nfcmobile.api

import org.eclipse.keyple.interop.jsonapi.client.api.CardIOException
import org.eclipse.keyple.interop.jsonapi.client.api.ReaderIOException

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class LocalNfcReader {

  var scanMessage: String
  var name: String

  /**
   * Suspends until a card is detected.
   *
   * This function suspends the current coroutine and waits until a card is detected in the reader.
   * It provides a coroutine-friendly alternative to synchronous polling or asynchronous callbacks,
   * and should be called from within a coroutine scope.
   *
   * @return `true` if a card was successfully detected and is present.
   * @throws ReaderIOException If an I/O error occurs while communicating with the reader.
   * @since 1.0.0
   */
  @Throws(ReaderIOException::class, kotlin.coroutines.cancellation.CancellationException::class)
  suspend fun waitForCardPresent(): Boolean

  /**
   * Starts monitoring the reader for card detection events asynchronously.
   *
   * When a card is detected in the reader, the provided [onCardDetected] callback is invoked. This
   * function does not block the calling thread and is suitable for use in event-driven or UI-based
   * applications.
   *
   * @param onCardDetected The callback function to invoke when a card is detected.
   * @throws ReaderIOException If an I/O error occurs while communicating with the reader.
   * @since 1.0.0
   */
  @Throws(ReaderIOException::class) fun startCardDetection(onCardDetected: () -> Unit)

  /**
   * Attempts to open the physical channel with the card.
   *
   * @throws ReaderIOException If an I/O error occurs while communicating with the reader.
   * @throws CardIOException If an I/O error occurs while communicating with the card.
   * @since 1.0.0
   */
  @Throws(ReaderIOException::class, CardIOException::class) fun openPhysicalChannel()

  /**
   * Closes safely the current physical channel.
   *
   * The physical channel may have been implicitly closed previously by a card withdrawal.
   *
   * @since 1.0.0
   */
  fun closePhysicalChannel()

  /**
   * Returns the power-on data. The power-on data is defined as the data retrieved by the reader
   * when the card is detected.
   *
   * In the case of a contactless reader, the reader decides what this data is. Contactless readers
   * provide a virtual ATR (partially standardized by the PC/SC standard), but other devices can
   * have their own definition, including for example elements from the anti-collision stage of the
   * ISO14443 protocol (ATQA, ATQB, ATS, SAK, etc).
   *
   * These data being variable from one reader to another, they are defined here in string format
   * which can be either a hexadecimal string or any other relevant information.
   *
   * @return a String containing the power-on data, or an empty String
   * @since 1.0.0
   */
  fun getPowerOnData(): String

  /**
   * Transmits an Application Protocol Data Unit (APDU) command to the smart card and receives the
   * response.
   *
   * @param commandApdu The command APDU to be transmitted.
   * @return The response APDU received from the smart card.
   * @throws ReaderIOException If an I/O error occurs while communicating with the reader.
   * @throws CardIOException If an I/O error occurs while communicating with the card.
   * @since 1.0.0
   */
  @Throws(ReaderIOException::class, CardIOException::class)
  fun transmitApdu(commandApdu: ByteArray): ByteArray

  /**
   * Releases the reader and safely stops any ongoing NFC polling operations.
   *
   * This method should be called when the reader is no longer needed to clean up system resources
   * and stop background processes such as card detection or polling loops.
   *
   * Implementations must ensure that the reader is left in a clean and reusable state after this
   * call.
   *
   * @since 1.0.0
   */
  fun releaseReader()
}
