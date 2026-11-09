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
    private lateinit var status: TextView
    private lateinit var heatValue: TextView
    private var heating = 60
    private val permissionAction = "com.mfix.printercontrol.USB_PERMISSION"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == permissionAction) {
                val dev = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && dev != null) openPrinter(dev)
                else showStatus("לא אושרה הרשאת USB")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = getSystemService(USB_SERVICE) as UsbManager
        val filter = IntentFilter(permissionAction)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        makeUi()
        connectUsb()
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
        val dev = devices.firstOrNull { d ->
            (0 until d.interfaceCount).any { i ->
                val f = d.getInterface(i)
                (0 until f.endpointCount).any { e ->
                    f.getEndpoint(e).direction == UsbConstants.USB_DIR_OUT &&
                    f.getEndpoint(e).type == UsbConstants.USB_ENDPOINT_XFER_BULK
                }
            }
        } ?: devices.first()
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
        for (i in 0 until dev.interfaceCount) {
            val face = dev.getInterface(i)
            val out = (0 until face.endpointCount).map { face.getEndpoint(it) }.firstOrNull {
                it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            } ?: continue
            val c = usb.openDevice(dev)
            if (c != null && c.claimInterface(face, true)) {
                conn = c; endpoint = out; intf = face
                showStatus("מחובר למדפסת USB. אפשר להדפיס.")
                return
            }
            c?.close()
        }
        showStatus("התקן זוהה, אך לא נפתח ערוץ USB להדפסה.")
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
            if (n <= 0) return false
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
            val ok = send(reset) && (heat.isEmpty() || send(heat)) && send(sample) &&
                send(byteArrayOf(0x1D, 0x56, 0x00))
            runOnUiThread { showStatus(if (ok) "דף הבדיקה נשלח." else "שליחה נכשלה. בדוק חיבור USB.") }
        }.start()
    }

    override fun onDestroy() {
        closePrinter()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
