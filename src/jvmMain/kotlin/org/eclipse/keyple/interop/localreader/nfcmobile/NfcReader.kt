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
import javax.smartcardio.Card
import javax.smartcardio.CardChannel
import javax.smartcardio.CardTerminal
import javax.smartcardio.CommandAPDU
import javax.smartcardio.TerminalFactory

private const val TAG = "NFCReader"

actual class LocalNfcReader(val readerNameFilter: String = "*") {
  actual var scanMessage: String = ""
  actual var name = "JvmNFC"

  private var card: Card? = null
  private var channel: CardChannel? = null
  private var reader: CardTerminal? = null

  private fun selectReader() {
    if (reader == null) {
      val terminalFactory = TerminalFactory.getDefault()
      val terminals = terminalFactory.terminals().list()
      Napier.d("Readers detected: $terminals")
      if (terminals.isEmpty()) {
        return
      }

      reader =
          terminals[0] // Use the first CardTerminal if not specified otherwise with a readerName
      if (readerNameFilter.isNotEmpty()) {
        for (terminal: CardTerminal in terminals) {
          if (terminal.name.contains(readerNameFilter, ignoreCase = true)) {
            reader = terminal
            name = terminal.name
            Napier.d(tag = TAG, message = "Using CardTerminal: ${terminal.name}")
            break
          }
        }
      }
    }
  }

  actual suspend fun waitForCardPresent(): Boolean {
    selectReader()
    reader?.let {
      it.waitForCardPresent(0)
      card = it.connect("T=1")
      Napier.d(tag = TAG, message = "Card present: $card")
      return true
    }
    return false
  }

  actual fun startCardDetection(onCardFound: () -> Unit) {
    selectReader()
    reader?.let {
      it.waitForCardPresent(0)
      card = it.connect("T=1")
      Napier.d(tag = TAG, message = "Card present: $card")
      onCardFound()
    }
  }

  actual fun openPhysicalChannel() {
    channel = card!!.basicChannel
  }

  actual fun closePhysicalChannel() {
    card?.disconnect(false)
    card = null
    Napier.d(tag = TAG, message = "Card closed")
  }

  @OptIn(ExperimentalStdlibApi::class)
  actual fun getPowerOnData(): String {
    val res = card?.atr?.bytes?.toHexString()
    return res ?: ""
  }

  @OptIn(ExperimentalStdlibApi::class)
  actual fun transmitApdu(commandApdu: ByteArray): ByteArray {
    Napier.d(tag = TAG, message = "-- APDU:")
    Napier.d(tag = TAG, message = "----> ${commandApdu.toHexString()}")
    val response = channel!!.transmit(CommandAPDU(commandApdu))
    Napier.d(tag = TAG, message = "<---- ${response.bytes.toHexString()}")
    return response.bytes
  }

  actual fun releaseReader() {
    closePhysicalChannel()
    reader = null
    Napier.d(tag = TAG, message = "Reader closed")
  }
}
