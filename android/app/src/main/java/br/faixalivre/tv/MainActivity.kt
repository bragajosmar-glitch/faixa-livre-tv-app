package br.faixalivre.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Faixa Livre TV para Android TV, Google TV e celular.
 * Abre o site do app e libera o que o navegador bloqueia em sites:
 *  - vídeo de endereços sem https;
 *  - servidores que não liberam a leitura por sites (CORS);
 *  - canais que exigem um cabeçalho "Referer" ou "User-Agent" específico.
 * Como o app carrega o site publicado, toda atualização do site chega aqui sozinha.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private val appUrl = "https://faixa-livre-tv.web.app/"
    private val cabecalhos = ConcurrentHashMap<String, Pair<String?, String?>>() // servidor -> (referer, user agent)
    private val erros = ConcurrentHashMap<String, String>() // endereço ou "host:servidor" -> último erro de rede

    // endereços que já funcionam bem no navegador: não mexer
    private val semMexer = listOf(
        "faixa-livre-tv.web.app", "faixa-livre-tv.firebaseapp.com",
        "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com",
        "youtube.com", "youtube-nocookie.com", "ytimg.com", "googlevideo.com",
        "dailymotion.com", "dmcdn.net", "cloudflare.com"
    )
    private val proprios = listOf("faixa-livre-tv.web.app", "faixa-livre-tv.firebaseapp.com")

    private fun dominio(host: String, lista: List<String>) = lista.any { host == it || host.endsWith(".$it") }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        setContentView(web)
        esconderBarras()

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.userAgentString = s.userAgentString + " FaixaLivreTVApp/1"

        web.setBackgroundColor(0xFF0B0B10.toInt())
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.addJavascriptInterface(Ponte(), "FaixaLivreTVAndroid")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val host = req.url.host ?: return false
                if (dominio(host, proprios)) return false
                // outros links (Lattes, Faixa Livre de músicas...) abrem no navegador do aparelho
                try { startActivity(Intent(Intent.ACTION_VIEW, req.url)) } catch (_: Exception) {}
                return true
            }

            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (req.method != "GET" || req.isForMainFrame) return null
                val u = req.url
                val scheme = u.scheme ?: return null
                if (scheme != "http" && scheme != "https") return null
                val host = u.host ?: return null
                if (dominio(host, semMexer)) return null
                return buscar(u.toString(), req.requestHeaders)
            }

            override fun onReceivedError(view: WebView, req: WebResourceRequest, error: WebResourceError) {
                if (req.isForMainFrame) view.loadDataWithBaseURL(null, SEM_INTERNET, "text/html", "utf-8", null)
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(appUrl)
    }

    /** Busca o vídeo direto (como um player faz) e devolve ao site já liberado para leitura. */
    private fun buscar(endereco: String, pedido: Map<String, String>): WebResourceResponse? {
        return try {
            var url = URL(endereco)
            var c = abrir(url, pedido)
            var saltos = 0
            // segue redirecionamentos, inclusive de http para https (o Android não faz isso sozinho)
            while (c.responseCode in 300..399 && c.getHeaderField("Location") != null && saltos < 6) {
                val proximo = URL(url, c.getHeaderField("Location"))
                c.disconnect()
                url = proximo
                saltos++
                c = abrir(url, pedido)
            }
            val code = c.responseCode
            val tipo = c.contentType ?: "application/octet-stream"
            val mime = tipo.substringBefore(";").trim()
            val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(tipo)?.groupValues?.get(1)?.trim()
            val saida = HashMap<String, String>()
            for ((k, v) in c.headerFields) {
                if (k == null || v.isEmpty()) continue
                val kl = k.lowercase()
                if (kl.startsWith("access-control") || kl == "content-encoding" || kl == "content-length" || kl == "transfer-encoding" || kl == "connection") continue
                saida[k] = v.joinToString(", ")
            }
            saida["Access-Control-Allow-Origin"] = "*"
            val corpo = if (code >= 400) (c.errorStream ?: ByteArrayInputStream(ByteArray(0))) else c.inputStream
            val motivo = c.responseMessage?.takeIf { it.isNotBlank() } ?: if (code >= 400) "Erro" else "OK"
            WebResourceResponse(mime, charset, code, motivo, saida, corpo)
        } catch (e: Exception) {
            val motivo = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
            if (erros.size > 400) erros.clear()
            erros[endereco] = motivo
            try { erros["host:" + URL(endereco).host] = motivo } catch (_: Exception) {}
            null
        }
    }

    private fun abrir(url: URL, pedido: Map<String, String>): HttpURLConnection {
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 20000
        c.instanceFollowRedirects = false
        for ((k, v) in pedido) {
            if (k.equals("Origin", true) || k.equals("Referer", true) || k.equals("Accept-Encoding", true)) continue
            c.setRequestProperty(k, v)
        }
        val h = cabecalhos[url.host]
        h?.first?.let { c.setRequestProperty("Referer", it) }
        h?.second?.let { c.setRequestProperty("User-Agent", it) }
        return c
    }

    /** O site chama estas funções para saber que está no app e pedir cabeçalhos especiais. */
    inner class Ponte {
        @JavascriptInterface
        fun platform(): String =
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) "android-tv" else "android"

        @JavascriptInterface
        fun setHeaders(url: String?, referer: String?, ua: String?) {
            try {
                val host = URL(url ?: return).host
                cabecalhos[host] = Pair(referer?.takeIf { it.isNotBlank() }, ua?.takeIf { it.isNotBlank() })
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun version(): String = "1"

        @JavascriptInterface
        fun lastError(url: String?): String {
            val u = url ?: return ""
            erros[u]?.let { return it }
            return try { erros["host:" + URL(u).host] ?: "" } catch (_: Exception) { "" }
        }
    }

    private fun js(codigo: String) = web.evaluateJavascript(codigo, null)

    /** Teclas do controle remoto que o site não recebe sozinho. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) {
                web.evaluateJavascript("(window.FaixaLivreTVBack && window.FaixaLivreTVBack()) ? 1 : 0") { r ->
                    if (r != "1") { if (web.canGoBack()) web.goBack() else finish() }
                }
            }
            return true
        }
        if (e.action == KeyEvent.ACTION_DOWN) {
            val acao = when (e.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> "chup"
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "chdown"
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> "playpause"
                KeyEvent.KEYCODE_INFO -> "info"
                KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_TV_DATA_SERVICE -> "guide"
                KeyEvent.KEYCODE_LAST_CHANNEL -> "last"
                else -> null
            }
            if (acao != null) { js("window.FaixaLivreTVKey && window.FaixaLivreTVKey('$acao')"); return true }
        }
        return super.dispatchKeyEvent(e)
    }

    @Suppress("DEPRECATION")
    private fun esconderBarras() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) esconderBarras()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }

    companion object {
        private const val SEM_INTERNET = """<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<body style="margin:0;height:100vh;display:grid;place-items:center;background:#0B0B10;color:#F5F5F7;font:18px sans-serif;text-align:center">
<div><h1 style="font-size:28px">Sem conexão</h1><p style="color:#9a9aa5">Confira a internet e tente de novo.</p>
<button autofocus onclick="location.href='https://faixa-livre-tv.web.app/'" style="font:600 18px sans-serif;padding:14px 30px;border:0;border-radius:999px;background:#fff;color:#000">Tentar de novo</button></div></body></html>"""
    }
}
