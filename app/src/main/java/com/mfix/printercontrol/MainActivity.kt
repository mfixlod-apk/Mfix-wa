package com.mfix.printercontrol

import android.app.*
import android.content.*
import android.hardware.usb.*
import android.os.*
import android.view.Gravity
import android.widget.*
import java.nio.charset.Charset

class MainActivity : Activity() {
    private lateinit var usb: UsbManager
    private var conn: UsbDeviceConnection? = null
    private var endpoint: UsbEndpoint? = null
    private var intf: UsbInterface? = null
    private var usbDetails = ""
    @Volatile private var transferError = ""
    private lateinit var status: TextView
    private lateinit var heatValue: TextView
    private var heating = 60
    private val permissionAction = "com.mfix.printercontrol.USB_PERMISSION"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == permissionAction) {
                val dev = getUsbDeviceFromIntent(intent)
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && dev != null) openPrinter(dev)
                else showStatus("לא אושרה הרשאת USB")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = getSystemService(USB_SERVICE) as UsbManager
        registerUsbPermissionReceiver()
        makeUi()
        connectUsb()
    }

    @Suppress("DEPRECATION")
    private fun registerUsbPermissionReceiver() {
        val filter = IntentFilter(permissionAction)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    @Suppress("DEPRECATION")
    private fun getUsbDeviceFromIntent(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }

    private fun makeUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 20, 22, 20)
            setBackgroundColor(0xFFF4F5F7.toInt())
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(TextView(this).apply {
            text = "MFIX Printer Control"
            textSize = 25f; gravity = Gravity.CENTER
            setTextColor(0xFF111827.toInt()); setPadding(0, 10, 0, 4)
        })
        body.addView(TextView(this).apply {
            text = "כלי USB למדפסת Xprinter XP-N160II"
            textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 16)
        })
        status = TextView(this).apply {
            textSize = 15f; setPadding(12, 14, 12, 14)
            setTextColor(0xFF155E75.toInt()); text = "מחפש מדפסת..."
        }
        body.addView(status)
        body.addView(button("חבר / רענן USB") { connectUsb() })
        body.addView(button("הדפס דף בדיקה רגיל") { printTest(false) })
        body.addView(button("הדפס דף בדיקת חימום") { printTest(true) })
        body.addView(TextView(this).apply {
            text = "עוצמת חימום – ניסיוני"
            textSize = 19f; setTextColor(0xFF111827.toInt()); setPadding(0, 20, 0, 6)
        })
        heatValue = TextView(this).apply { textSize = 16f }
        body.addView(heatValue)
        body.addView(SeekBar(this).apply {
            max = 40; progress = 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    heating = 40 + p; heatValue.text = "זמן חימום: $heating"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        heatValue.text = "זמן חימום: $heating"
        body.addView(TextView(this).apply {
            text = "הערה: ESC 7 היא פקודת הגדרת חימום כללית בחלק ממדפסות ESC/POS, אך לא אומתה לכל גרסאות הקושחה של N160II. השינוי נשלח לפני דף הבדיקה בלבד. התחל בערך 60 ואל תעלה בחדות."
            textSize = 13f; setTextColor(0xFF92400E.toInt()); setPadding(8, 10, 8, 16)
        })
        body.addView(TextView(this).apply {
            text = "מהירות וצפיפות"
            textSize = 19f; setTextColor(0xFF111827.toInt()); setPadding(0, 16, 0, 6)
        })
        body.addView(TextView(this).apply {
            text = "לא נמצא במדריך הפומבי של N160II פרוטוקול מאומת לשינוי מהירות מנגנון או צפיפות פנימית. לכן האפליקציה לא שולחת פקודות ניחוש. אפשר למדוד מהירות בפועל בהמשך."
            textSize = 14f; setTextColor(0xFF374151.toInt()); setPadding(0, 4, 0, 14)
        })
        body.addView(button("נתק מדפסת") { closePrinter(); showStatus("המדפסת נותקה") })
        scroll.addView(body); page.addView(scroll); setContentView(page)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f
        setOnClickListener { action() }
    }

    private fun showStatus(s: String) { status.text = s }

    private fun connectUsb() {
        val devices = usb.deviceList.values
        if (devices.isEmpty()) { showStatus("לא זוהתה מדפסת. בדוק כבל USB OTG והפעלה."); return }
        fun hasBulkOut(d: UsbDevice, printerOnly: Boolean): Boolean =
            (0 until d.interfaceCount).any { i ->
                val face = d.getInterface(i)
                (!printerOnly || face.interfaceClass == UsbConstants.USB_CLASS_PRINTER) &&
                    (0 until face.endpointCount).any { e ->
                        val ep = face.getEndpoint(e)
                        ep.direction == UsbConstants.USB_DIR_OUT &&
                            ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    }
            }
        val dev = devices.firstOrNull { hasBulkOut(it, true) }
            ?: devices.firstOrNull { hasBulkOut(it, false) }
            ?: devices.first()
        showStatus("נמצא USB VID=${dev.vendorId}, PID=${dev.productId}. מבקש הרשאה...")
        if (usb.hasPermission(dev)) openPrinter(dev) else {
            val intent = Intent(permissionAction).setPackage(packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            usb.requestPermission(dev, PendingIntent.getBroadcast(this, 0, intent, flags))
        }
    }

    private fun openPrinter(dev: UsbDevice) {
        closePrinter()
        val candidates = (0 until dev.interfaceCount).map { dev.getInterface(it) }
            .sortedBy { if (it.interfaceClass == UsbConstants.USB_CLASS_PRINTER) 0 else 1 }
        for (face in candidates) {
            val out = (0 until face.endpointCount).map { face.getEndpoint(it) }.firstOrNull {
                it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            } ?: continue
            val c = usb.openDevice(dev)
            if (c != null && c.claimInterface(face, true)) {
                conn = c; endpoint = out; intf = face
                usbDetails = "VID=${dev.vendorId} PID=${dev.productId}, class=${face.interfaceClass}, interface=${face.id}, endpoint=0x${out.address.toString(16)}, packet=${out.maxPacketSize}"
                showStatus("USB נפתח. $usbDetails")
                return
            }
            c?.close()
        }
        showStatus("התקן זוהה, אך לא נפתח ערוץ USB להדפסה. בדוק הרשאה/ממשק.")
    }

    private fun closePrinter() {
        try { intf?.let { conn?.releaseInterface(it) } } catch (_: Exception) {}
        try { conn?.close() } catch (_: Exception) {}
        conn = null; endpoint = null; intf = null
    }

    private fun send(data: ByteArray): Boolean {
        val c = conn ?: return false
        val ep = endpoint ?: return false
        var pos = 0
        while (pos < data.size) {
            val chunk = data.copyOfRange(pos, minOf(pos + 2048, data.size))
            val n = c.bulkTransfer(ep, chunk, chunk.size, 5000)
            if (n <= 0) {
                transferError = "bulkTransfer החזיר $n; endpoint=0x${ep.address.toString(16)}, packet=${ep.maxPacketSize}, bytes=${chunk.size}, $usbDetails"
                return false
            }
            pos += n
        }
        return true
    }

    private fun printTest(withHeat: Boolean) {
        if (conn == null) { connectUsb(); Toast.makeText(this, "חבר תחילה את המדפסת", Toast.LENGTH_SHORT).show(); return }
        Thread {
            val reset = byteArrayOf(0x1B, 0x40)
            val heat = if (withHeat) byteArrayOf(0x1B, 0x37, heating.toByte(), 7, 50) else byteArrayOf()
            val sample = ("\nMFIX PRINTER TEST\nXP-N160II / USB\n" +
                (if (withHeat) "Heating value: $heating\n" else "Default profile\n") +
                "--------------------------------\n" +
                "1234567890 ABCDEFGHIJ\n" +
                "THE QUICK BROWN FOX 0123456789\n" +
                "||||||||||||||||||||||||||||||||\n" +
                "--------------------------------\n\n\n").toByteArray(Charset.forName("CP437"))
            transferError = ""
            val result = if (!send(reset)) "נכשל באיפוס: $transferError"
                else if (heat.isNotEmpty() && !send(heat)) "נכשל בפקודת חימום: $transferError"
                else if (!send(sample)) "נכשל בשליחת הטקסט: $transferError"
                else if (!send(byteArrayOf(0x1D, 0x56, 0x00))) "הטקסט נשלח, אך חיתוך נכשל: $transferError"
                else "דף הבדיקה נשלח בהצלחה. $usbDetails"
            runOnUiThread { showStatus(result) }
        }.start()
    }

    override fun onDestroy() {
        closePrinter()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
