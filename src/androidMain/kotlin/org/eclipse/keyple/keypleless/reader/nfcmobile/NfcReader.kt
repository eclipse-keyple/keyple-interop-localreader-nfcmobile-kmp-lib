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

import android.app.Activity
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import io.github.aakira.napier.Napier
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import org.eclipse.keyple.keypleless.distributed.client.spi.CardIOException

private const val TAG = "NFCReader"

actual class LocalNfcReader(private val activity: Activity) {
  private var tag: Tag? = null
  private var isoDep: IsoDep? = null
  private var channel: Channel<Tag>? = null
  actual var scanMessage: String = ""
  actual var name = "AndroidNFC"

  actual suspend fun startCardDetection(onCardFound: () -> Unit) {
    Napier.d(tag = TAG, message = "startCardDetection")
    enableForeground { onCardFound() }
  }

  private fun enableForeground(cardCallback: (Tag) -> Unit) {
    var flags = 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      flags = FLAG_IMMUTABLE
    }
    val pendingIntent =
        PendingIntent.getActivity(
            activity.applicationContext,
            0,
            Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags)
    val extras = Bundle()
    extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 75) // default value is 125ms
    NfcAdapter.getDefaultAdapter(activity.applicationContext)
        .enableReaderMode(
            activity,
            { tag ->
              this.tag = tag
              cardCallback(tag)
            },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            extras)
    NfcAdapter.getDefaultAdapter(activity.applicationContext)
        .enableForegroundDispatch(activity, pendingIntent, null, null)
  }

  //    private fun cardPresent(newTag: Tag) {
  //        Napier.d(tag = TAG, message = "Card detected, notify...")
  //        channel.trySend(newTag)
  //    }

  actual fun releaseReader() {
    Napier.d(tag = TAG, message = "stopCardDetection")
    disableForeground()
    channel?.cancel(CancellationException("Android Reader released"))
    this.tag = null
  }

  private fun disableForeground() {
    NfcAdapter.getDefaultAdapter(activity.applicationContext).disableForegroundDispatch(activity)
    NfcAdapter.getDefaultAdapter(activity.applicationContext).disableReaderMode(activity)
  }

  actual suspend fun waitForCardPresent(): Boolean {
    Napier.d(tag = TAG, message = "Wait for card")
    channel = Channel()
    enableForeground {
      Napier.d(tag = TAG, message = "Card detected, notify...")
      channel?.trySend(it)
    }
    try {
      tag = channel?.receive()
      Napier.d(tag = TAG, message = "Card found!")
      return true
    } catch (e: CancellationException) {
      Napier.d(tag = TAG, message = "Reader released")
      return false
    }
  }

  /**
   * Attempts to open the physical channel (to establish communication with the card). <exception
   * cref="ReaderNotFoundException">If the communication with the reader has failed.</exception>
   * <exception cref="CardIOException">If the communication with the card has failed.</exception>
   */
  actual fun openPhysicalChannel() {
    Napier.d(tag = TAG, message = "openPhysicalChannel")
    if (!tag!!.techList.contains(IsoDep::class.qualifiedName)) {
      throw CardIOException("Card is not IsoDep")
    }
    try {
      if (isoDep == null) {
        Napier.d(tag = TAG, message = "grab isodep")
        isoDep = IsoDep.get(tag)
      }
      if (!isoDep!!.isConnected) {
        Napier.d(tag = TAG, message = "connect")
        isoDep!!.connect()
      }
    } catch (e: IOException) {
      throw CardIOException(e.message!!)
    }
  }

  /**
   * Attempts to close the current physical channel. The physical channel may have been implicitly
   * closed previously by a card withdrawal.
   *
   * <exception cref="ReaderNotFoundException">If the communication with the reader has
   * failed.</exception>
   */
  actual fun closePhysicalChannel() {
    try {
      Napier.d(tag = TAG, message = "close")
      isoDep?.close()
    } catch (_: Exception) {
      // Ignore any error while closing the tag on Android...
    }
    isoDep = null
  }

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
  actual fun getPowerOnData(): String {
    // TODO
    return ""
  }

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
  @OptIn(ExperimentalStdlibApi::class)
  actual fun transmitApdu(commandApdu: ByteArray): ByteArray {
    Napier.d(tag = TAG, message = "-- APDU:")
    Napier.d(tag = TAG, message = "----> ${commandApdu.toHexString()}")
    try {
      val res = isoDep!!.transceive(commandApdu)
      Napier.d(tag = TAG, message = "<---- ${res.toHexString()}")
      return res
    } catch (e: SecurityException) {
      throw CardIOException(e.message!!)
    } catch (e: IOException) {
      throw CardIOException(e.message!!)
    } catch (e: TagLostException) {
      throw CardIOException(e.message!!)
    }
  }
}
