package com.capri.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val prefs = getSharedPreferences("capri", 0)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        fun btn(t: String, f: () -> Unit) { root.addView(Button(this).apply { text = t; setOnClickListener { f() } }) }

        root.addView(TextView(this).apply { text = "Capri"; textSize = 28f })
        val key = EditText(this).apply {
            hint = "Chave da API Anthropic"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
        }
        root.addView(key)
        val model = EditText(this).apply { hint = "Modelo (ex: claude-haiku-5-5)"; setText(prefs.getString("model", Brain.MODEL)) }
        root.addView(model)
        val pv = EditText(this).apply { hint = "Chave Picovoice (palavra Capri)"; setText(prefs.getString("pv", "")) }
        root.addView(pv)
        btn("1. Salvar chave e modelo") {
            prefs.edit().putString("key", key.text.toString().trim())
                .putString("pv", pv.text.toString().trim())
                .putString("model", model.text.toString().trim().ifBlank { Brain.MODEL }).apply()
            Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
        }
        btn("0. Escolher modelo local (.litertlm)") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 7)
        }
        btn("Escolher imagem da Capri (PNG)") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 8)
        }
        btn("2. Conceder permissões") {
            val p = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(p.toTypedArray(), 1)
        }
        btn("3. Ativar acessibilidade (Capri)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("4. Permitir sobreposição (abrir apps)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("5. Iniciar Capri") { startForegroundService(Intent(this, CapriService::class.java)) }
        btn("Parar Capri") { stopService(Intent(this, CapriService::class.java)) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if ((req != 7 && req != 8) || res != RESULT_OK) return
        val target = if (req == 7) "model.litertlm" else "capri.png"
        Toast.makeText(this, "Copiando, aguarde...", Toast.LENGTH_LONG).show()
        Thread {
            val msg = try {
                contentResolver.openInputStream(uri)!!.use { i ->
                    java.io.File(filesDir, target).outputStream().use { o -> i.copyTo(o) }
                }
                "Pronto. Pare e inicie a Capri de novo."
            } catch (e: Exception) { "Erro: ${e.message}" }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }
}
