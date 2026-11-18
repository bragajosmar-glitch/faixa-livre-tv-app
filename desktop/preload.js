// Faixa Livre TV (Windows): avisa o site de que ele está dentro do app e permite pedir cabeçalhos especiais para alguns canais.
const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("FaixaLivreTVApp", {
  platform: "desktop",
  version: "1",
  setHeaders: (url, referer, ua) => ipcRenderer.send("fltv-headers", { url: String(url || ""), referer: String(referer || ""), ua: String(ua || "") })
});
