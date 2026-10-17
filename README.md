# BanglaKeys (Android keyboard)

## APK বানানো
1. এই ফোল্ডারের সব ফাইল একটি GitHub repo-তে push করুন।
2. Repo → Actions → "Build APK" → Run workflow (push করলে নিজে থেকেই চলে)।
3. কাজ শেষ হলে Artifacts থেকে `BanglaKeys-apk` ডাউনলোড করুন → ভেতরে `app-debug.apk`।
4. ফোনে ইনস্টল করে অ্যাপ খুলুন → "Enable" → "Select"।

কোনো বাইরের লাইব্রেরি নেই, native code নেই — তাই armeabi-v7a (32-bit ARM), arm64, x86 সব ফোনেই চলে (minSdk 21 = Android 5.0)।
