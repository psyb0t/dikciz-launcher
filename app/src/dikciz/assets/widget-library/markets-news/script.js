(() => {
    const PRICE_URL = "https://api.coinbase.com/v2/prices/BTC-USD/spot";
    const NEWS_URL = "https://api.gdeltproject.org/api/v2/doc/doc?" + new URLSearchParams({
        query:"(economy OR inflation OR monetary) sourcelang:english", mode:"artlist", format:"json", maxrecords:"8", timespan:"24h", sort:"datedesc"
    });
    const TIMEOUT_MS = 15000;
    const MAX_BYTES = 65536;
    const MAX_HEADLINES = 8;
    const byId = id => document.getElementById(id);
    let saved = {};
    let busy = false;
    async function readJson(url) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
        try {
            const response = await fetch(url, {signal:controller.signal, credentials:"omit", cache:"no-store"});
            if (!response.ok) throw new Error("HTTP " + response.status);
            if (!response.body) throw new Error("Response streaming unavailable");
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let count = 0;
            let text = "";
            while (true) {
                const chunk = await reader.read();
                if (chunk.done) break;
                count += chunk.value.byteLength;
                if (count > MAX_BYTES) { controller.abort(); throw new Error("Response exceeds 64 KiB"); }
                text += decoder.decode(chunk.value, {stream:true});
            }
            return JSON.parse(text + decoder.decode());
        } finally { clearTimeout(timer); }
    }
    function webUrl(value) {
        if (typeof value !== "string" || value.length > 2048) return null;
        try { const url = new URL(value); return ["https:","http:"].includes(url.protocol) && !url.username && !url.password ? url.href : null; }
        catch { return null; }
    }
    function render() {
        if (saved.market?.amount && Number.isFinite(Number(saved.market.amount)) && /^\d+(\.\d+)?$/.test(saved.market.amount)) {
            byId("price").textContent = "$" + Number(saved.market.amount).toLocaleString(undefined, {minimumFractionDigits:2, maximumFractionDigits:2});
            byId("price-status").textContent = "Coinbase spot. Fetched " + new Date(saved.market.fetchedAt).toLocaleString() + ". Saved quote.";
        }
        const articles = Array.isArray(saved.news?.articles) ? saved.news.articles.slice(0, MAX_HEADLINES) : [];
        byId("news").replaceChildren(...articles.flatMap(article => {
            if (!article || typeof article !== "object") return [];
            const url = webUrl(article.url);
            if (!url || typeof article.title !== "string") return [];
            const item = document.createElement("li");
            const button = document.createElement("button");
            button.type = "button";
            button.className = "news-title";
            button.textContent = article.title.slice(0, 300);
            button.addEventListener("click", async () => {
                try {
                    const result = await dikciz.command("intent", {intentType:"activity", action:"android.intent.action.VIEW", data:url});
                    byId("status").textContent = result.activityLaunches?.[0]?.verified ? "Publisher opened." : "Browser requested. Check Android.";
                } catch (error) { byId("status").textContent = "Could not open publisher: " + error.message; }
            });
            const source = document.createElement("small");
            source.textContent = new URL(url).hostname + (article.seendate ? " · Seen " + article.seendate : "");
            item.append(button, source);
            return [item];
        }));
        if (saved.news?.fetchedAt) byId("news-status").textContent = articles.length + " saved headlines. Fetched " + new Date(saved.news.fetchedAt).toLocaleString() + ".";
    }
    async function refresh() {
        if (busy) return;
        busy = true;
        byId("refresh").disabled = true;
        byId("status").textContent = "Fetching Coinbase and GDELT...";
        const results = await Promise.allSettled([
            readJson(PRICE_URL).then(response => {
                const value = response.data;
                if (value?.base !== "BTC" || value.currency !== "USD" || typeof value.amount !== "string" || !/^\d+(\.\d+)?$/.test(value.amount) || !Number.isFinite(Number(value.amount)) || Number(value.amount) <= 0) throw new Error("Invalid BTC/USD quote");
                saved.market = {amount:value.amount, fetchedAt:new Date().toISOString()};
            }),
            readJson(NEWS_URL).then(response => {
                if (!Array.isArray(response.articles)) throw new Error("Invalid news response");
                saved.news = {fetchedAt:new Date().toISOString(), articles:response.articles.slice(0, MAX_HEADLINES).filter(article => article && typeof article === "object" && webUrl(article.url) && typeof article.title === "string").map(article => ({title:article.title.slice(0,300),url:webUrl(article.url),seendate:typeof article.seendate === "string" ? article.seendate.slice(0,32) : ""}))};
            })
        ]);
        render();
        const failures = results.flatMap((result, index) => result.status === "rejected" ? [(index === 0 ? "Coinbase: " : "GDELT: ") + result.reason.message] : []);
        try { await selfWidget.patchState({...selfWidget.state, ...saved}); }
        catch (error) { failures.push("Save failed: " + error.message); }
        byId("status").textContent = failures.length ? failures.join(". ") + ". Previous saved results remain dated." : "Updated. Results are saved on this phone.";
        busy = false;
        byId("refresh").disabled = false;
    }
    byId("refresh").addEventListener("click", refresh);
    window.addEventListener("dikciz-ready", () => { saved = {...selfWidget.state}; render(); });
})();
