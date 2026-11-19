// Faixa Livre TV para Windows (e Mac/Linux).
// Abre o site do app numa janela própria e libera o que o navegador bloqueia em sites:
//  - vídeo de endereços sem https;
//  - servidores que não liberam a leitura por sites (CORS);
//  - canais que exigem um cabeçalho "Referer" ou "User-Agent" específico.
// Como o app carrega o site publicado, toda atualização do site chega aqui sozinha.
const { app, BrowserWindow, shell, ipcMain, Menu } = require("electron");
const path = require("path");

const APP_URL = process.env.FLTV_URL || "https://faixa-livre-tv.web.app/";   // FLTV_URL: só para testes
const PROPRIOS = /(^|\.)(faixa-livre-tv\.web\.app|faixa-livre-tv\.firebaseapp\.com)$/i;
// serviços que já funcionam bem: não mexer
const SEM_MEXER = /(^|\.)(google\.com|googleapis\.com|gstatic\.com|googleusercontent\.com|youtube\.com|youtube-nocookie\.com|ytimg\.com|googlevideo\.com|dailymotion\.com|dmcdn\.net|cloudflare\.com)$/i;

app.commandLine.appendSwitch("autoplay-policy", "no-user-gesture-required");
if (!app.requestSingleInstanceLock()) app.quit();

const cabecalhos = new Map(); // servidor -> {referer, ua}
ipcMain.on("fltv-headers", (_e, d) => {
  try { cabecalhos.set(new URL(d.url).host, { referer: d.referer || "", ua: d.ua || "" }); } catch {}
});

// último erro de rede de cada endereço: o site mostra o motivo real quando um canal não abre
const erros = new Map();
ipcMain.handle("fltv-erro", (_e, url) => {
  const u = String(url || ""); if (erros.has(u)) return erros.get(u);
  let host = ""; try { host = new URL(u).host; } catch {}
  return erros.get("host:" + host) || "";
});

const hostDe = u => { try { return new URL(u).hostname; } catch { return ""; } };

const SEM_INTERNET = `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><title>Faixa Livre TV</title>
<body style="margin:0;height:100vh;display:grid;place-items:center;background:#0B0B10;color:#F5F5F7;font:16px system-ui,sans-serif;text-align:center">
<div><h1 style="font-size:26px">Sem conexão</h1><p style="color:#9a9aa5">Confira a internet e tente de novo.</p>
<button onclick="location.href='${APP_URL}'" style="font:600 16px system-ui;padding:12px 26px;border:0;border-radius:999px;background:#fff;color:#000;cursor:pointer">Tentar de novo</button></div></body></html>`;

let janela = null;

function criarJanela() {
  Menu.setApplicationMenu(null);
  janela = new BrowserWindow({
    width: 1400, height: 860, minWidth: 760, minHeight: 480,
    backgroundColor: "#0B0B10", title: "Faixa Livre TV",
    icon: path.join(__dirname, "build", "icon.png"),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      sandbox: true,
      allowRunningInsecureContent: true,   // deixa abrir vídeo de endereços sem https
      backgroundThrottling: false
    }
  });
  const sessao = janela.webContents.session;

  // cabeçalhos pedidos pelo site para canais específicos
  sessao.webRequest.onBeforeSendHeaders((d, cb) => {
    let host = ""; try { host = new URL(d.url).host; } catch {}
    const h = cabecalhos.get(host), rh = d.requestHeaders;
    if (h) { if (h.referer) rh["Referer"] = h.referer; if (h.ua) rh["User-Agent"] = h.ua; }
    cb({ requestHeaders: rh });
  });

  // libera a leitura dos vídeos pelo site (o servidor do canal não precisa autorizar)
  sessao.webRequest.onHeadersReceived((d, cb) => {
    const host = hostDe(d.url);
    if (d.resourceType === "mainFrame" || !host || PROPRIOS.test(host) || SEM_MEXER.test(host)) return cb({});
    const h = { ...d.responseHeaders };
    for (const k of Object.keys(h)) if (/^access-control-allow-(origin|credentials)$/i.test(k)) delete h[k];
    h["Access-Control-Allow-Origin"] = ["*"];
    cb({ responseHeaders: h });
  });

  sessao.webRequest.onErrorOccurred(d => {
    if (d.resourceType === "mainFrame") return;
    erros.set(d.url, d.error); try { erros.set("host:" + new URL(d.url).host, d.error); } catch {}
    if (erros.size > 400) erros.delete(erros.keys().next().value);
  });

  // janelas novas: o login do Google abre dentro do app; o resto vai para o navegador do computador
  janela.webContents.setWindowOpenHandler(({ url }) => {
    if (/(^|\.)accounts\.google\.com$/i.test(hostDe(url))) return { action: "allow" };
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  janela.webContents.on("will-navigate", (e, url) => {
    if (!PROPRIOS.test(hostDe(url))) { e.preventDefault(); if (/^https?:/i.test(url)) shell.openExternal(url); }
  });

  // F11 tela cheia · F12 ferramentas de diagnóstico · F5 ou Ctrl+R recarrega · Ctrl+Shift+R recarrega limpando a cópia guardada do site
  janela.webContents.on("before-input-event", async (e, input) => {
    if (input.type !== "keyDown") return;
    const k = (input.key || "").toLowerCase();
    if (input.key === "F11"){ e.preventDefault(); janela.setFullScreen(!janela.isFullScreen()); }
    else if (input.key === "F12"){ e.preventDefault(); janela.webContents.toggleDevTools(); }
    else if ((input.control || input.meta) && input.shift && k === "r"){
      e.preventDefault();
      try { await sessao.clearCache(); await sessao.clearStorageData({ storages: ["serviceworkers", "cachestorage"] }); } catch {}
      janela.webContents.reloadIgnoringCache();
    }
    else if (input.key === "F5" || ((input.control || input.meta) && k === "r")){ e.preventDefault(); janela.webContents.reload(); }
  });

  janela.webContents.on("did-fail-load", (_e, _code, _desc, _url, principal) => {
    if (principal) janela.loadURL("data:text/html;charset=utf-8," + encodeURIComponent(SEM_INTERNET));
  });

  janela.loadURL(APP_URL);
}

app.on("second-instance", () => { if (janela) { if (janela.isMinimized()) janela.restore(); janela.focus(); } });
app.whenReady().then(criarJanela);
app.on("window-all-closed", () => app.quit());
