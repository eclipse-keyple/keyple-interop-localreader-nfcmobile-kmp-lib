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
@file:OptIn(ExperimentalStdlibApi::class, BetaInteropApi::class)

package org.eclipse.keyple.interop.localreader.nfcmobile.api

import io.github.aakira.napier.Napier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.keyple.interop.jsonapi.client.api.CardIOException
import org.eclipse.keyple.interop.jsonapi.client.api.ReaderIOException
import platform.CoreNFC.NFCISO7816APDU
import platform.CoreNFC.NFCISO7816TagProtocol
import platform.CoreNFC.NFCPollingISO14443
import platform.CoreNFC.NFCTagProtocol
import platform.CoreNFC.NFCTagReaderSession
import platform.CoreNFC.NFCTagReaderSessionDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy
import platform.posix.uint8_t

private const val TAG = "NFCReader"

internal object ReaderInstance {
  private var session: NFCTagReaderSession? = null
  var channel: Channel<NfcTag?>? = null
  private var nfcTag: NfcTag? = null
  val queue = dispatch_queue_create("NFC_WORKER_QUEUE", null)

  var getErrorMsg: (e: Exception) -> String = { e -> "Error: ${e.message}" }

  fun sessionStarted(session: NFCTagReaderSession) {
    ReaderInstance.session = session
  }

  fun sendTagToChannel() {
    channel?.trySend(nfcTag)
  }

  fun selectCard(session: NFCTagReaderSession, card: NFCISO7816TagProtocol?) {
    ReaderInstance.session = session
    if (card != null) {
      nfcTag = NfcTag(card)
    } else {
      nfcTag = null
    }
  }

  fun getCard(): NfcTag? {
    return nfcTag
  }

  fun getSession(): NFCTagReaderSession? {
    return session
  }

  fun clearSession() {
    session = null
  }

  fun error(err: String?) {
    session?.let {
      if (err != null) {
        session!!.invalidateSessionWithErrorMessage(err)
      } else {
        session!!.invalidateSession()
      }
    }
    session = null
    channel?.cancel(CancellationException(err))
  }
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class LocalNfcReader(private val getErrorMsg: (e: Exception) -> String) {
  actual var scanMessage: String = "Place your card on the top of your iPhone"
  actual var name = "iOS-NFC"

  private lateinit var readerCallback: NativeNfcReaderCallback

  init {
    ReaderInstance.getErrorMsg = getErrorMsg
  }

  private fun isReadingAvailable(): Boolean {
    return NFCTagReaderSession.readingAvailable()
  }

  actual suspend fun waitForCardPresent(): Boolean {
    startiOSCardDetection { ReaderInstance.sendTagToChannel() }
    // Blocking until a tag is pushed in the channel...
    Napier.d(tag = TAG, message = "Wait until tag detected...")
    val nfcTag = ReaderInstance.channel?.receive()
    Napier.d(tag = TAG, message = "Tag detected!")
    return nfcTag != null
  }

  actual fun startCardDetection(onCardDetected: () -> Unit) {
    startiOSCardDetection { ReaderInstance.getCard()?.let { onCardDetected() } }
  }

  private fun startiOSCardDetection(onConnected: () -> Unit) {
    Napier.d(tag = TAG, message = "Start scanning")
    if (!isReadingAvailable()) {
      throw ReaderIOException("NFC is not available")
    }

    ReaderInstance.channel = Channel()
    val session = ReaderInstance.getSession()

    if (session == null) {
      // all the NcfTagReader callbacks will end up on NFC_WORKER_QUEUE
      MainScope().launch(Dispatchers.Main) {
        readerCallback = NativeNfcReaderCallback(onConnected)
        val newSession =
            NFCTagReaderSession(NFCPollingISO14443, readerCallback, ReaderInstance.queue)
        newSession.setAlertMessage(scanMessage)
        newSession.beginSession()
        Napier.d(tag = TAG, message = "New session started")
      }
    } else {
      Napier.d(tag = TAG, message = "Session already active")
    }
  }

  actual fun releaseReader() {
    closeSession()
    ReaderInstance.channel?.cancel(CancellationException("Reader closed"))
  }

  actual fun openPhysicalChannel() {
    ReaderInstance.getSession()?.setAlertMessage(scanMessage)
  }

  actual fun closePhysicalChannel() {
    closeSession()
  }

  private fun closeSession(error: Exception? = null) {
    var errMsg = ""
    error?.let { errMsg = getErrorMsg(error) }
    Napier.d(tag = TAG, message = "Closing NFC reader session")
    val session = ReaderInstance.getSession()
    session?.let {
      if (errMsg.isNotEmpty()) {
        session.invalidateSessionWithErrorMessage(errorMessage = errMsg)
      } else {
        session.invalidateSession()
      }
      Napier.d(tag = TAG, message = "Closed NFCTagReaderSession")
    }
    ReaderInstance.clearSession()
  }

  actual fun getPowerOnData(): String {
    return "Unavailable"
  }

  @OptIn(ExperimentalStdlibApi::class)
  actual fun transmitApdu(commandApdu: ByteArray): ByteArray {
    val card = ReaderInstance.getCard()!!
    val apduData = commandApdu.toNSData()
    val isoApdu = NFCISO7816APDU(apduData)
    var res: ByteArray
    runBlocking { res = card.sendCommand(isoApdu) }
    return res
  }
}

private const val MIN_RESPONSE_LENGTH = 2

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData = memScoped {
  NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray =
    ByteArray(this@toByteArray.length.toInt()).apply {
      usePinned { memcpy(it.addressOf(0), this@toByteArray.bytes, this@toByteArray.length) }
    }

internal class NfcTag(private val tag: NFCISO7816TagProtocol) {
  // this call happens on a random thread (coroutine default dispatcher)
  suspend fun sendCommand(isoApdu: NFCISO7816APDU): ByteArray = suspendCoroutine { cont ->
    Napier.d(tag = TAG, message = "-- APDU:")
    Napier.d(tag = TAG, message = "----> ${commandApdu.toHexString()}")
    // this callback happens on the NFC_WORKER_QUEUE thread
    val onCommandResult = { data: NSData?, sw1: uint8_t, sw2: uint8_t, error: NSError? ->
      if (error != null) {
        Napier.d(tag = TAG, message = "Send command APDU failed $error")
        val err = CardIOException(error.localizedDescription)
        ReaderInstance.error(ReaderInstance.getErrorMsg(err))
        cont.resumeWithException(err)
      } else {
        // the response is *at least* SW1 and SW2
        var length = MIN_RESPONSE_LENGTH
        if (data != null) {
          length += data.length.toInt()
        }
        // data might be null or empty
        val dataArray =
            if (data != null && length > MIN_RESPONSE_LENGTH) {
              data.toByteArray()
            } else ByteArray(0)

        val array =
            ByteArray(length) { pos ->
              when (pos) {
                length - 2 -> sw1.toByte()
                length - 1 -> sw2.toByte()
                else -> dataArray[pos]
              }
            }
        Napier.d(tag = TAG, message = "<---- ${array.toHexString()}")
        cont.resume(array) // resume waiting coroutine
      }
    }

    tag.sendCommandAPDU(isoApdu, onCommandResult)
  }
}

internal class NativeNfcReaderCallback(private val onConnected: () -> Unit) :
    NSObject(), NFCTagReaderSessionDelegateProtocol {

  override fun tagReaderSessionDidBecomeActive(session: NFCTagReaderSession) {
    Napier.d(tag = TAG, message = "TagReaderSession did become active")
    ReaderInstance.sessionStarted(session)
  }

  // This is called from NFC_WORKER_THREAD
  override fun tagReaderSession(session: NFCTagReaderSession, didDetectTags: List<*>) {
    Napier.d(tag = TAG, message = "tagReaderSession callback")
    if (didDetectTags.size > 1) {
      session.invalidateSessionWithErrorMessage("More than 1 tag detected. Present only 1 tag.")
      ReaderInstance.clearSession()
      return
    }

    val tag = (didDetectTags[0] as NFCTagProtocol)
    val onConnected = { error: NSError? ->
      error?.let {
        Napier.d(tag = TAG, message = "Error connecting session: ${error.localizedDescription}")
        session.invalidateSessionWithErrorMessage(error.localizedDescription)
        ReaderInstance.clearSession()
        return@let
      }

      val isoTag = tag.asNFCISO7816Tag()
      if (isoTag == null) {
        session.invalidateSessionWithErrorMessage("Unsupported tag type")
        ReaderInstance.clearSession()
      } else {
        Napier.d(tag = TAG, message = "Tag session connected")
        ReaderInstance.selectCard(session, isoTag)
        onConnected()
      }
    }
    session.connectToTag(tag, onConnected)
  }

  override fun tagReaderSession(session: NFCTagReaderSession, didInvalidateWithError: NSError) {
    Napier.d(
        tag = TAG,
        message =
            "Tag reader session invalidated: ${didInvalidateWithError.code} ${didInvalidateWithError.localizedDescription} userInfo:${didInvalidateWithError.userInfo}")
    ReaderInstance.error(didInvalidateWithError.localizedDescription)
  }
}
