# حالة الاتصال الخلفي

## الوضع الحالي

- يستخدم تطبيقا العميل والإدارة مصادقة Parse/Back4App. الإعدادات تُقرأ من `local.properties` أو متغيرات بيئة CI، ولا ينبغي وضع Master Key في تطبيق أندرويد.
- تمت مواءمة تدفق طلبات شحن الرصيد على Back4App: إنشاء الطلب مربوط بمستخدم Parse، المراجعة عبر Cloud Code، ورصيد المحفظة يُحدّث على الخادم. Room كاش وليس مصدراً للحقيقة.
- Cloud Code الأمني موجود في `cloud/main.js`. إعداد Role وCLPs ونشر Cloud Code يظل خطوة يدوية في لوحة Back4App.
- بقية التطبيق ما زالت في مرحلة ترحيل؛ توجد مستودعات/خدمات Firestore وFirebase قديمة في مسارات أخرى (الإعلانات والإعدادات والبيانات المساندة). لا يعني إصلاح الشحن اكتمال ترحيل كل ميزات التطبيق.

## متطلبات التشغيل

1. أضف إعدادات Back4App محلياً أو GitHub Actions وفق `docs/BACK4APP_TOPUP_SETUP_AR.md`.
2. أنشئ Role باسم `Admin` وأضف إليه حساب الإدارة الأول.
3. انشر `cloud/main.js` واضبط CLP/ACL والجداول المطلوبة قبل اختبار التطبيق.
4. راجع الطلبات القديمة التي تحتوي روابط Parse File عامة؛ ACL سجل الطلب لا يسحب صلاحية رابط الملف القديم.

## تشغيل بناء/اختبار Android

```bash
./gradlew :app:testDebugUnitTest :admin:testDebugUnitTest :app:assembleDebug :admin:assembleDebug
```

راجع [دليل إعداد شحن Back4App](docs/BACK4APP_TOPUP_SETUP_AR.md) للاختبار والصلاحيات.
