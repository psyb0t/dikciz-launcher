# Recognition languages and models

speechLanguages queries the selected provider on Android 13+. mode defaults onDevice with no fallback; system explicitly selects the installed system provider. language is optional BCP-47, default device language. timeoutMilliseconds defaults 30000, range 1..120000.

Page with offset/limit (default 0/50, maximum 1000000/200). Items contain language and availability: installedOnDevice, pendingOnDevice, supportedOnDevice or online. The same language can occur in several categories. Follow hasMore. Each page re-queries; model/provider changes can change membership.

speechModelDownload requires language and Android 14+. It can use network data and trigger provider consent. It waits for available (provider confirmed usable) or scheduled (not yet available), or fails explicitly. Timeout/interruption leaves outcome unknown and may not stop provider work.

While it runs, speechModelStatus from another connection returns the latest sessionId, requested/downloading state and nullable progressPercent. Supply sessionId to reject a different current request. Only one support/model request owns the slot; other requests return hardware_busy.

Status retains only the last request in this process. It does not resume observation after scheduled completion, timeout or restart. Query speechLanguages later to verify availability. Use jobStart when a client cannot hold the download request open.

Do not claim downloaded bytes from an available callback; the model may already exist. Android offers no download cancellation guarantee. Ordinary command permissions and owner-review recovery are shared.

Full guide: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/speech-models.md
