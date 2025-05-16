package com.example.sms_spam
import okhttp3.MediaType.Companion.toMediaType
import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.Telephony
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 100
    private lateinit var smsListView: ListView
    private lateinit var btnLoadSms: Button
    private val smsList = mutableListOf<String>()
    private val smsBodyList = mutableListOf<String>() // for sending to backend

    private val client = OkHttpClient()
    private val API_URL = "https://sms-spam-detect-gytc.onrender.com/api/predict"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        smsListView = findViewById(R.id.smsListView)
        btnLoadSms = findViewById(R.id.btnLoadSms)

        btnLoadSms.setOnClickListener {
            checkSmsPermissionAndLoad()
        }

        smsListView.setOnItemClickListener { _, _, position, _ ->
            val smsBody = smsBodyList[position]
            sendToBackend(smsBody)
        }
    }

    private fun checkSmsPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), SMS_PERMISSION_CODE)
        } else {
            loadSmsFromInbox()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSmsFromInbox()
            } else {
                Toast.makeText(this, "SMS permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSmsFromInbox() {
        smsList.clear()
        smsBodyList.clear()
        val cursor: Cursor? = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null, null, Telephony.Sms.DATE + " DESC"
        )

        cursor?.let {
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                val address = cursor.getString(addressIdx)
                val body = cursor.getString(bodyIdx)
                smsList.add("From: $address\n$body")
                smsBodyList.add(body)
                if (smsList.size >= 50) break // Limit for performance
            }
            cursor.close()
        }
        smsListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, smsList)
    }

    private fun sendToBackend(message: String) {
        val json = JSONObject()
        json.put("message", message)
        val body = RequestBody.create("application/json; charset=utf-8".toMediaType(), json.toString())
        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val respStr = response.body?.string()
                runOnUiThread {
                    try {
                        val resultJson = JSONObject(respStr)
                        val result = resultJson.optString("result", "Unknown")
                        showResultDialog(message, result)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Invalid response", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showResultDialog(sms: String, result: String) {
        AlertDialog.Builder(this)
            .setTitle("Spam Check Result")
            .setMessage("Message:\n$sms\n\nResult: $result")
            .setPositiveButton("OK", null)
            .show()
    }
}