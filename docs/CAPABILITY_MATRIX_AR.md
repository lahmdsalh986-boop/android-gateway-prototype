# مصفوفة قدرات BRIXAR Gateway

## 1. ما يستطيع APK عادي تنفيذه فعليًا

يستطيع التطبيق فتح sockets عادية، إنشاء HTTP CONNECT أو SOCKS-style application relay، اختيار Wi-Fi/Ethernet كشبكة صاعدة، إنشاء TCP Data Tunnel مستقل إلى خادم الاختبار، فصل Control Channel عن Data Channel، تنفيذ PING/ACK، عدّ البايتات الفعلية في كل مرحلة، وقياس زمن الاستجابة. يستطيع كذلك طلب `LocalOnlyHotspot` إذا كانت نسخة Android والتصاريح والجهاز تدعم ذلك، لكنه شبكة محلية فقط ولا تعني مشاركة Internet.

يستطيع التطبيق طلب موافقة `VpnService` الرسمية، إنشاء TUN، إضافة عنوان ومسار ضيق، وقراءة الحزم التي يوجهها Android إلى TUN. يستطيع عرض حالة الجهاز ونسخة Android وحالة VPN ووجود Root أو System flags، وقراءة جدول ARP عندما يسمح النظام بذلك. لا تُستخدم أي من هذه النتائج لإضافة عميل وهمي أو عداد وهمي.

## 2. ما يحتاج Root أو System/Privileged

يحتاج اعتراض حزم كل عملاء Soft AP أو tethering وربطها قسرًا بتطبيق Gateway إلى تكامل نظام أو Root أو مكوّن OEM. ويحتاج تشغيل Internet Tethering برمجيًا من تطبيق عادي إلى صلاحيات خاصة مثل `TETHER_PRIVILEGED` أو مسار `WRITE_SETTINGS` وسياسة carrier؛ لا يكفي طلب إذن وقت التشغيل العادي. يحتاج إعداد NAT/forwarding وiptables وقواعد routing خاصة إلى صلاحيات نظام أو Root أو جهاز Gateway مخصص.

يحتاج جعل الـTUN هو نقطة عبور حزم الأجهزة البعيدة، بدل أن يكون VPN لتطبيقات المستخدم/الملف الشخصي فقط، إلى دعم نظام/تنفيذ OEM يربط tethering stack بالـVPN أو forwarding hook. لا يكتسب التطبيق هذه القدرة بمجرد تسجيل `BIND_VPN_SERVICE`.

## 3. ما يحتاج Gateway خارجي أو لا توفره Public SDK

لا توفر Public Android SDK ضمانًا لتسليم كل حزم عملاء Hotspot إلى APK عادي. ولا توفر API عامة لإنشاء gateway transparently مع NAT كامل داخل تطبيق عادي عبر الأجهزة ونسخ Android. إذا كان المطلوب توجيه كل البروتوكولات حتى التي لا تستخدم Proxy، مع forwarding دائم وموثوق، فالحل المناسب Router/Gateway خارجي أو Android system image مخصص.

## التشخيص داخل التطبيق

يعرض BRIXAR Gateway نتيجة مستقلة باسم **التوجيه الشفاف لعملاء Hotspot**. في APK عادي تكون النتيجة `غير متاح` مع سببها. ويعرض أسفلها **المسار المتاح حاليًا: Explicit Proxy**. لا تُحسب جلسة Proxy أو عداداتها على أنها دليل نجاح للتوجيه الشفاف.

عند استخدام Test Client مع Proxy، تكون مراحل القياس الحقيقية هي: `Client → Gateway proxy`, ثم `Gateway → Data Tunnel`, ثم `Data Tunnel → Server`, ثم العودة بالعكس. إذا لم يرسل العميل عبر Proxy فلن تظهر جلسة Proxy ولن تتغير عدادات Data Tunnel؛ وهذا هو السلوك المطلوب بدل اختلاق Traffic.
