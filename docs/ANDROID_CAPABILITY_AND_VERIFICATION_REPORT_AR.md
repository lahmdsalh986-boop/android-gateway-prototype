# تقرير التحليل والتنفيذ — Android Gateway Prototype

**الإصدار:** 0.1.4  
**التاريخ:** 25 سبتمبر 2026  
**المؤلف:** Manus AI

## الخلاصة التنفيذية

تم بناء **نموذج Android أصلي قابل للبناء والتثبيت** مع خادم اختبار حقيقي وتطبيق عميل Android مستقل. يختبر النموذج مسارًا محددًا وصريحًا بدقة: يفتح هاتف العميل جلسة `HTTP CONNECT` إلى مستمع محلي داخل تطبيق البوابة عبر شبكة Wi‑Fi، ثم ينشئ تطبيق البوابة قناة TCP مستقلة يملكها هو إلى خادم الاختبار، ويعيد البايتات في الاتجاه المعاكس. ترفض قناة البيانات الاستخدام عند كون الشبكة الصاعدة خلوية؛ فهي تسمح فقط بـ Wi‑Fi أو Ethernet. أضيف كذلك مسار `VpnService/TUN` تشخيصي حقيقي بموافقة المستخدم، لكنه يثبت فقط ما يدخل TUN فعليًا ولا يزعم اعتراض عملاء الـHotspot.

> **النتيجة الهندسية الحالية:** مسار التطبيق الصريح `Client → Wi‑Fi → Gateway App → TCP Data Tunnel → Test Server` مبني، جرى تجميعه بنجاح، وخادم الاختبار اجتاز عمليًا اختبار 10 MiB وSHA‑256 في الاتجاهين. أمّا إثبات هذا المسار بين هاتفَي Android حقيقيين فلم يُنفّذ داخل بيئة التطوير، ولذلك حالته **NOT VERIFIED ON PHYSICAL ANDROID DEVICES** ولا توجد نتيجة مصطنعة تدّعي خلاف ذلك.

النتيجة الأهم لتحليل APIs هي أن التطبيق العادي غير الجذور لا يملك API عامة وموثقة لاعتراض كل حزم العملاء التي يمررها **Android tethering/Soft AP** بصورة شفافة. لا يجعل `VpnService` التطبيق ملتقطًا عامًا لحزم عملاء الـhotspot، كما أن `LocalOnlyHotspot` مصمم لاتصال محلي بلا Internet forwarding. لذلك اختير في النسخة 0.1 نموذج **Explicit Proxy** بدلاً من الادعاء بتوجيه شفاف غير مضمون.

## A. ما الذي تم بناؤه

يحتوي المشروع على ثلاثة مكوّنات حقيقية. تطبيق **Android Gateway Prototype** مكتوب بـKotlin ويشغّل خدمة foreground تحتوي على مستمع TCP لـHTTP CONNECT. عند تلقي العميل طلب CONNECT، ينشئ التطبيق اتصال TCP منفصلًا إلى خادم الاختبار عبر `Network.socketFactory` الخاصة بالشبكة الصاعدة. يحمل هذا الاتصال ترويسة `AGP/1 CONNECT` ثم يمرر بايتات العميل والخادم في الاتجاهين.

تطبيق **Gateway Test Client** هو تطبيق Android مستقل. ينشئ CONNECT صريحًا إلى البوابة، ثم يطلب تنزيل 10 MiB محددة حتميًا من الخادم، ويحسب SHA‑256 على الهاتف. بعد ذلك يولّد 10 MiB حتمية ويرفعها عبر المسار نفسه، ثم يقارن SHA‑256 الذي يرده الخادم. لا تعتمد النتيجة على رسالة اتصال؛ بل تعتمد على التطابق الفعلي للهاش.

خادم الاختبار هو `server/agp_test_server.py`، ويشغّل ثلاث منافذ TCP: منفذ بيانات افتراضي `9000`، ومنفذ تحكم `9001`، ومنفذ حمولة `10080`. الخادم مقيد عمدًا بأن يتصل فقط بخدمة الحمولة المحلية `127.0.0.1` أو ما يعادلها، ولذلك ليس open proxy ولا بوابة إنترنت عامة.

تحتوي واجهة Gateway الآن على زر **Start TUN diagnostic**. بعد موافقة VPN ينشئ `GatewayVpnService` واجهة TUN بعنوان `10.99.0.2/32` ومسارًا ضيقًا إلى `10.99.0.0/24`، ويقرأ IP packets التي يسلّمها Android إلى الواجهة ويعدّها في `TUN RX`. لا تُستخدم هذه النتيجة لإثبات أن حزم عميل Soft AP دخلت TUN؛ ذلك يبقى اختبارًا ميدانيًا مستقلًا وحالته `NOT VERIFIED`.

يحتوي الإصدار 0.1.3 أيضًا على زر **Run real Gateway → Server 1 MiB test**. هذا الزر يفتح Data Tunnel فعليًا، يطلب 1 MiB من خادم الحمولة، يقرأ البايتات، يحسب SHA‑256، ويقارن قيمة الخادم. كما تقيس قناة التحكم زمن `PING/ACK` الحقيقي، ويظهر مؤشر `DATA MOVING` فقط عندما تتغير عدادات البايتات؛ لا يعتمد على مؤقت لعرض نشاط وهمي.

يضيف الإصدار 0.1.4 صفحة **قدرات الجهاز** داخل BRIXAR Gateway. تعرض الصفحة Android والجهاز، Root، System privileges، VPN، Hotspot، Tethering، والتوجيه الشفاف لعملاء Hotspot. كما تعرض عملاء `/proc/net/arp` عندما يوفر النظام هذا الجدول. هذه القراءات لا تنشئ عملاء أو نجاحًا وهميًا. وتعرض صراحةً: **التوجيه الشفاف لبيانات عملاء Hotspot غير متاح لتطبيق Android عادي على هذا الجهاز. المسار المتاح حاليًا: Explicit Proxy.**

## B. Android APIs المستخدمة وتحليل القابلية

| API أو آلية | استخدامها في النموذج | النتيجة والحد |
| --- | --- | --- |
| `WifiManager.startLocalOnlyHotspot()` | طلب شبكة Wi‑Fi محلية اختيارية للعميل وعرض SSID/بيانات الاتصال التي يعيدها النظام. | توفر اتصالًا محليًا بين الأجهزة القريبة ولا تعد بتوجيه Internet أو تسليم حزم العملاء إلى التطبيق. [1] |
| `ConnectivityManager` و`NetworkCapabilities` | قراءة الشبكة الصاعدة النشطة وقبول Wi‑Fi أو Ethernet فقط ورفض cellular. | يختار التطبيق socket مرتبطًا بالشبكة التي كشفها النظام؛ ينبغي اختبار فقدان الشبكة وتبديلها ميدانيًا. [2] |
| `Network.getSocketFactory()` | فتح TCP Data Tunnel مرتبطًا بالشبكة الصاعدة بدلاً من ترك اختيار الشبكة غامضًا. | يوجّه **socket التطبيق** فقط؛ لا يفرض توجيهًا على جهاز العميل ولا يوفر اعتراضًا شفافًا. [3] |
| sockets عادية و`ServerSocket` | مستمع CONNECT محلي، ونقل البايتات، والعدادات الفعلية. | يبرهن نقل حركة عميل التزم بضبط proxy؛ لا يشمل التطبيقات أو البروتوكولات التي تتجاهل proxy. |
| `VpnService` | يستخدمه الإصدار 0.1.3 في تشخيص TUN بموافقة المستخدم. | ينشئ TUN لحزم توجهها Android إلى VPN الخاصة بالمستخدم/الملف الشخصي. لا توجد ضمانة API عامة بأن حركة عملاء tethering تدخل هذا الـTUN. [4] |
| `GatewayVpnService` | مكوّن تشخيصي فعلي في 0.1.1 ينشئ TUN بعد موافقة المستخدم ويقرأ الحزم التي تصل إليه. | يثبت قناة TUN نفسها فقط. المسار الضيق لا يلتقط تلقائيًا حركة عملاء Wi‑Fi البعيدين. |
| `VpnService.Builder.addRoute()` و`protect()` | غير مستخدمين لأنهما لا يحققان شرط عميل Wi‑Fi. | `addRoute()` يختار وجهات VPN، و`protect()` يستثني socket التطبيق من دورة VPN؛ لا ينشئان hook لتوجيه عملاء hotspot. [4] |
| `TetheringManager` | تم تحليله ولم يستخدم. | بدء tethering ومراقبته ليسا API عامة لتسليم packet stream للتطبيق. كما توجد قيود privileged أو special access وcarrier entitlement. [5] |
| `NetworkRequest` | تم تحليله كتحسين لاحق ولم يستخدم في 0.1.3. | يمكن طلب Wi‑Fi صراحةً ثم ربط sockets بالـNetwork المرجع، لكنه لا يحل ingress من عميل hotspot. [2] [3] |

يحتاج `LocalOnlyHotspot` في التطبيق المستهدف Android 13+ إلى `CHANGE_WIFI_STATE` و`NEARBY_WIFI_DEVICES` كإذن وقت التشغيل، ويحتاج مستمع foreground إلى `FOREGROUND_SERVICE` و`FOREGROUND_SERVICE_DATA_SYNC`. يستخدم التطبيق كذلك `INTERNET` و`ACCESS_NETWORK_STATE`. في إصدارات Android المستقبلية التي تطبق إذن الشبكة المحلية للتطبيقات المستهدفة الجديدة، يلزم تقييم `ACCESS_LOCAL_NETWORK` قبل اعتماد مستمع proxy محلي. [1] [6]

## C. المخطط النهائي لمسار البيانات

```text
                                  TCP/AGP-1 (Wi‑Fi أو Ethernet فقط)
+------------------+   HTTP CONNECT   +---------------------------+   +----------------+
| Gateway Test     | ───────────────► | Android Gateway Prototype | ─►| Test Server    |
| Client (Android) | ◄─────────────── |  ServerSocket :8080       | ◄─| Data :9000     |
+------------------+      Wi‑Fi       |  Network-bound TCP tunnel |   | Payload :10080 |
                                     +---------------------------+   +----------------+

                                          TCP/Control منفصل
                                     Gateway ───────────────────────► Server :9001
                                     PING n / ACK n فقط؛ لا Payload
```

يدخل العميل إلى البوابة صراحةً عبر عنوان IP الخاص بهاتف البوابة والمنفذ `8080`. بعد قبول CONNECT، لا يخرج الاتصال مباشرة إلى Internet؛ بل يفتح التطبيق جلسة `AGP/1 CONNECT` إلى `Test Server:9000`. لا تقبل قناة الاختبار في الخادم إلا هدف الحمولة `127.0.0.1:10080`، ومن ثم تبقى التجربة مضبوطة. ترويسة CONNECT لا تمرر كحمولة إلى الخادم؛ أما البايتات التي تليها فتمرر كاملة في الاتجاهين وتُحسب عند كل من جانب Wi‑Fi وجانب النفق.

## D. نتيجة Client → Server

**خادم الاختبار: PASS محليًا.** أرسل اختبار البروتوكول المحلي 10 MiB مولدة حتميًا إلى منفذ حمولة الخادم، وقرأ الخادم كل البايتات، ثم أعاد SHA‑256. القيمة المرسلة والمستلمة هي:

> `6eaac8e418644b1043e3c8db7c40b304eea99a634cd01b9f8576f60e6850daa6`

**Client Android → Gateway Android → Server: NOT VERIFIED.** التطبيق والـAPK وخط الاختبار موجودة، لكن هذه البيئة لا تحتوي هاتفَي Android متصلين بالشبكة لتنفيذ اختبار ميداني. يتطلب النجاح فتح تطبيق العميل على هاتف ثانٍ متصل بشبكة Wi‑Fi نفسها أو LocalOnlyHotspot، وإدخال IP البوابة، ثم ظهور `CLIENT → SERVER … PASS` مع نفس الهاش في واجهة العميل وسجل الخادم.

## E. نتيجة Server → Client

**خادم الاختبار: PASS محليًا.** أنشأ الخادم حمولة تنزيل مقدارها 10 MiB وجرى استلامها كاملة وحساب الهاش محليًا. القيمة المتوقعة والمحسوبة متطابقتان:

> `6eaac8e418644b1043e3c8db7c40b304eea99a634cd01b9f8576f60e6850daa6`

**Server → Gateway Android → Client Android: NOT VERIFIED.** التنفيذ موجود لكنه ينتظر الاختبار الفيزيائي. شرط النجاح هو ظهور `SERVER → CLIENT … PASS` في تطبيق العميل، وعدادات `Tunnel RX` و`Wi‑Fi TX` غير صفرية في تطبيق البوابة، وسجل `server→client payload` في الخادم.

## F. نتيجة SHA‑256

| اتجاه الاختبار الذي نُفذ | الحجم | SHA‑256 المتوقع | SHA‑256 المستلم | الحالة |
| --- | ---: | --- | --- | --- |
| Server harness → local protocol client | 10 MiB | `6eaac8e…e6850daa6` | `6eaac8e…e6850daa6` | **PASS** |
| Local protocol client → server harness | 10 MiB | `6eaac8e…e6850daa6` | `6eaac8e…e6850daa6` | **PASS** |
| Android client → Android gateway → server | 10 MiB | ينتظر التنفيذ | ينتظر التنفيذ | **NOT VERIFIED** |
| Server → Android gateway → Android client | 10 MiB | ينتظر التنفيذ | ينتظر التنفيذ | **NOT VERIFIED** |

لا ينبغي استبدال سطرَي NOT VERIFIED بأي استنتاج من نجاح تجميع APK أو نجاح harness محلي.

## G. عدادات RX/TX ودليل النقل

واجهة البوابة تعرض أربعة عدادات حقيقية تحدث في لحظة النسخ: `Wi‑Fi RX` و`Wi‑Fi TX` لبايتات جلسة العميل، و`Data Tunnel RX` و`Data Tunnel TX` لبايتات جلسة AGP/1. تسجل أيضًا فتح وإغلاق العميل، إنشاء وإغلاق النفق، أخطاء الاتصال، وأحداث ACK الخاصة بالتحكم. لا تحسب قناة التحكم payload العميل؛ فهي لا ترسل إلا handshake و`PING/ACK`.

عند الاختبار الميداني الناجح مع تنزيل ورفع 10 MiB، يتوقع أن تكون عدادات البيانات في الاتجاه المقابل غير صفرية وتقارب الحمولة مع فروق بسيطة للترويسات، بينما تبقى أحداث التحكم بوحدات صغيرة من البايتات. يجب حفظ لقطة من شاشة البوابة وسجل الخادم كمرفقات دليل؛ لا توجد لقطة ميدانية بعد، لذلك القيم النهائية **NOT VERIFIED**.

## H. هل نجح Data Tunnel؟

**نجح كتنفيذ وبروتوكول وخادم اختبار:** تم بناء التطبيقين بنجاح، كما اجتاز خادم البيانات وقناة التحكم اختبارًا محليًا حقيقيًا للاتجاهين وSHA‑256. قناة البيانات ترفض صراحةً الشبكة الخلوية ولا تُنشأ إلا على Wi‑Fi أو Ethernet.

**لم يُثبت بعد بين أجهزة Android حقيقية:** يلزم تنفيذ خطوات الاختبار المرفقة وتسجيل أدلة الهاتفين. لذلك لا يصح وصف نجاح Android end-to-end بأنه مؤكد في هذه المرحلة.

## I. هل نجح ربط Wi‑Fi Client Traffic بالـData Tunnel؟

**نعم، على مستوى التصميم والتنفيذ لتدفق client-proxy الصريح؛ NOT VERIFIED ميدانيًا.** لأن العميل يفتح `HTTP CONNECT` إلى تطبيق البوابة، تصل البايتات فعليًا إلى `ServerSocket` الخاص بالتطبيق ثم تُرحّل داخل Data Tunnel. هذا هو المسار القابل للدعم في تطبيق عادي بدون root.

**لا، ليس كاعتراض شفاف لتدفق Android tethering العام.** لا توفر Android API عامة ضمانًا بأن كل حركة الأجهزة المتصلة بـtethering أو Soft AP ستُسلّم إلى `VpnService` أو socket في التطبيق. يجب عدم التسويق للنموذج على أنه يحقق هذا النمط حتى يتوفر تكامل نظام/OEM أو جهاز مخصص. [4] [5]

## J. ما الذي يحتاج صلاحيات أو قدرات إضافية؟

يتطلب LocalOnlyHotspot موافقات Wi‑Fi runtime، ويتطلب foreground service إذن الخدمة. يطلب `VpnService` موافقة VPN صريحة من المستخدم، لكنه لا يحل مشكلة عملاء tethering. يحتاج `TetheringManager` في حالات كثيرة `TETHER_PRIVILEGED`، وهو signature/privileged ولا يستطيع التطبيق العادي طلبه، أو يحتاج `WRITE_SETTINGS` كصلاحية خاصة، ثم يبقى خاضعًا لاستحقاق المشغل وسياسة الجهاز. [5]

قد يتطلب مسار `LocalOnlyHotspot + Wi‑Fi uplink` دعم STA+AP concurrency من شريحة Wi‑Fi والـOEM. يجب فحص `WifiManager.isStaApConcurrencySupported()` في النسخة التالية قبل بدء hotspot، وتجربة الهاتف المستهدف؛ لا ينبغي افتراض التوازي. [7]

## K. ما الذي لا يستطيع التطبيق العادي فعله؟

لا يستطيع التطبيق العادي، باستخدام public SDK فقط، إضافة قواعد NAT/iptables، أو الالتصاق بواجهة Soft AP لالتقاط packet stream، أو إجبار Android tethering على توجيه كل عملاء Wi‑Fi عبر tunnel مملوك للتطبيق، أو ضمان اعتراض HTTPS plaintext. يحدث TLS relay هنا كبايتات end-to-end؛ لا يفك التطبيق تشفير HTTPS. أي اعتراض محتوى HTTPS يتطلب تصميم ثقة صريحًا وشهادات موثوقة من العميل، ولا يمكن افتراضه. [8]

لا ينبغي أيضًا اعتبار `LocalOnlyHotspot` بديلاً عن tethering؛ توثيق Android يصفه كشبكة محلية بلا Internet access. التطبيق يستطيع استخدامه كـLAN لعميل proxy-aware، لكن traffic forwarding يتم فقط لأن العميل اختار proxy والتطبيق فتح نفقه المستقل. [1]

## L. أفضل انتقال من Prototype إلى Gateway Phone حقيقي

المرحلة التالية الصحيحة هي إتمام الاختبار الميداني على مصفوفة صغيرة من هواتف حقيقية وتسجيل نوع الشبكة ونسخة Android وOEM ودعم STA+AP ونتائج الانقطاع وإعادة التشغيل. إذا كان منتج Gateway Phone يتطلب فقط عملاء متعاونين، يمكن تطوير proxy صريح إلى SOCKS5 وHTTP CONNECT موثق، واستخدام TLS للنفق ومصادقة للجهاز، وربط socket الصادر بشبكة Wi‑Fi مطلوبة بواسطة `NetworkRequest` وcallbacks بدلاً من الاعتماد على الشبكة النشطة فقط.

أما إذا كان الشرط غير قابل للتفاوض هو توجيه **كل** حزم عملاء Wi‑Fi بصورة شفافة، فالانتقال الصحيح ليس ادعاء حل داخل تطبيق Android عادي. الخيارات الواقعية هي جهاز gateway خارجي أو router، أو صورة نظام/OEM integration مع صلاحيات منصة وشبكات مناسبة، أو جهاز Android مخصص خاضع لإدارة كاملة مع اختبار لكل إصدار. يوفر ذلك مكانًا صحيحًا لـNAT والتوجيه والسياسة، بدلاً من الاعتماد على مسار tethering داخلي غير مكشوف للتطبيق.

## حالة الاختبارات المنفذة

| الاختبار | الحالة | الدليل |
| --- | --- | --- |
| تجميع `gateway-debug.apk` | **PASS** | Gradle build ناجح؛ package `com.example.androidgateway`، minSdk 29، targetSdk 35. |
| تجميع `gateway-test-client-debug.apk` | **PASS** | Gradle build ناجح؛ package `com.example.gatewaytestclient`، minSdk 29، targetSdk 35. |
| Harness data server → local client، 10 MiB + SHA‑256 | **PASS** | تطابق الهاش الكامل. |
| Local client → harness data server، 10 MiB + SHA‑256 | **PASS** | تطابق الهاش الكامل. |
| Control handshake، `PING 1` → `ACK 1` | **PASS** | استجاب منفذ control منفصل. |
| Client Android → Gateway Android → Server | **NOT VERIFIED** | لا توجد أجهزة Android فعلية متاحة في البيئة. |
| Server → Gateway Android → Client Android | **NOT VERIFIED** | لا توجد أجهزة Android فعلية متاحة في البيئة. |
| LocalOnlyHotspot مع Wi‑Fi uplink متزامن | **NOT VERIFIED** | يعتمد على الجهاز وSTA+AP concurrency. |
| قطع النفق وإعادة الاتصال على هاتفين | **NOT VERIFIED** | سينفذ وفق الإجراء المرفق. |

## المراجع

[1]: https://developer.android.com/reference/android/net/wifi/WifiManager#startLocalOnlyHotspot(android.net.wifi.WifiManager.LocalOnlyHotspotCallback,android.os.Handler) "WifiManager.startLocalOnlyHotspot API reference"
[2]: https://developer.android.com/reference/android/net/ConnectivityManager#requestNetwork(android.net.NetworkRequest,%20android.net.ConnectivityManager.NetworkCallback) "ConnectivityManager.requestNetwork API reference"
[3]: https://developer.android.com/reference/android/net/Network "Network API reference"
[4]: https://developer.android.com/reference/android/net/VpnService.Builder#establish() "VpnService.Builder.establish API reference"
[5]: https://developer.android.com/reference/android/net/TetheringManager "TetheringManager API reference"
[6]: https://developer.android.com/privacy-and-security/local-network-permission "Android local network permission guidance"
[7]: https://developer.android.com/reference/android/net/wifi/WifiManager#isStaApConcurrencySupported() "WifiManager.isStaApConcurrencySupported API reference"
[8]: https://developer.android.com/privacy-and-security/security-config "Network security configuration"
