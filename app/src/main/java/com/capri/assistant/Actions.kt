package com.capri.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import org.json.JSONObject

object Notes {
    private fun f(ctx: Context) = java.io.File(ctx.filesDir, "notes.md")
    fun read(ctx: Context): String = f(ctx).takeIf { it.exists() }?.readText()?.takeLast(3000) ?: ""
    fun add(ctx: Context, n: String) = f(ctx).appendText("- ${n.trim()}\n")
}

object Actions {
    /** Retorna a pergunta de confirmação se a ação for sensível; senão null. */
    fun describe(name: String, i: JSONObject): String? = when (name) {
        "send_whatsapp", "send_sms" -> "Vou enviar para ${i.optString("contact")}: ${i.optString("message")}. Confirma?"
        "call" -> "Vou ligar para ${i.optString("contact")}. Confirma?"
        else -> null
    }

    fun run(ctx: Context, name: String, i: JSONObject): String = try {
        when (name) {
            "open_app" -> openApp(ctx, i.getString("app"))
            "send_whatsapp" -> {
                val num = number(ctx, i.getString("contact")) ?: return "Contato não encontrado."
                launch(ctx, Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/$num?text=${Uri.encode(i.getString("message"))}")).setPackage("com.whatsapp"))
                Thread.sleep(3000)
                if (CapriAccessibility.instance?.clickByLabel(listOf("Enviar", "Send")) == true) "Mensagem enviada."
                else "Abri a conversa, mas não consegui tocar em enviar. Ative a acessibilidade."
            }
            "send_sms" -> {
                val num = number(ctx, i.getString("contact")) ?: return "Contato não encontrado."
                @Suppress("DEPRECATION")
                val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                sms.sendTextMessage("+$num", null, i.getString("message"), null, null)
                "SMS enviado."
            }
            "call" -> {
                val num = number(ctx, i.getString("contact")) ?: return "Contato não encontrado."
                launch(ctx, Intent(Intent.ACTION_CALL, Uri.parse("tel:+$num")))
                "Ligando."
            }
            "navigate" -> if (CapriAccessibility.instance?.nav(i.getString("action")) == true) "Feito." else "Acessibilidade desativada."
            "read_screen" -> CapriAccessibility.instance?.screenText() ?: "Acessibilidade desativada."
            "click_text" -> if (CapriAccessibility.instance?.clickByLabel(listOf(i.getString("text"))) == true)
                "Toquei em ${i.getString("text")}." else "Não achei esse botão na tela."
            "type_text" -> if (CapriAccessibility.instance?.typeText(i.getString("text")) == true) "Digitado."
                else "Não achei um campo de texto."
            "scroll" -> if (CapriAccessibility.instance?.scroll(i.getString("direction") == "down") == true) "Rolei." else "Nada para rolar."
            "wait" -> { Thread.sleep(minOf(i.optInt("seconds", 2), 10) * 1000L); "Esperei." }
            "open_url" -> { launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(i.getString("url")))); "Abrindo." }
            "set_alarm" -> {
                launch(ctx, Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                    .putExtra(android.provider.AlarmClock.EXTRA_HOUR, i.getInt("hour"))
                    .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, i.getInt("minute"))
                    .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, i.optString("label", "Capri"))
                    .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true))
                "Alarme criado."
            }
            "remember" -> { Notes.add(ctx, i.getString("note")); "Anotado." }
            "search_web" -> { launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(i.getString("query"))))); "Pesquisando." }
            "set_timer" -> {
                launch(ctx, Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                    .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, i.getInt("seconds"))
                    .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, i.optString("label", "Capri"))
                    .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true))
                "Timer criado."
            }
            "media" -> {
                val am = ctx.getSystemService(android.media.AudioManager::class.java)
                val code = when (i.getString("action")) {
                    "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                    "prev" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
                "Feito."
            }
            "volume" -> {
                val am = ctx.getSystemService(android.media.AudioManager::class.java)
                repeat(minOf(i.optInt("steps", 3), 15)) {
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                        if (i.getString("direction") == "up") android.media.AudioManager.ADJUST_RAISE
                        else android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                }
                "Volume ajustado."
            }
            else -> "Ação desconhecida."
        }
    } catch (e: Exception) { "Erro: ${e.message}" }

    private fun launch(ctx: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        (CapriAccessibility.instance ?: ctx).startActivity(intent)
    }

    private fun openApp(ctx: Context, app: String): String {
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(0).filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        val a = apps.firstOrNull { pm.getApplicationLabel(it).toString().equals(app, true) }
            ?: apps.firstOrNull { pm.getApplicationLabel(it).toString().contains(app, true) }
            ?: return "Não achei o app $app."
        launch(ctx, pm.getLaunchIntentForPackage(a.packageName)!!)
        return "Abrindo ${pm.getApplicationLabel(a)}."
    }

    /** Devolve só dígitos com DDI (assume Brasil 55 se faltar). */
    private fun number(ctx: Context, who: String): String? {
        var raw = who.filter { it.isDigit() }
        if (raw.length < 8) {
            ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$who%"), null)
                ?.use { if (it.moveToFirst()) raw = it.getString(0).filter { c -> c.isDigit() } else return null }
        }
        return if (raw.length in 10..11) "55$raw" else raw
    }
}
