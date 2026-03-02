# 🧪 ZeroAd Testing Checklist

**Tanggal:** 1 Maret 2026  
**Versi:** 1.2.0+3 (Development)  
**Last Commit:** `6f28ec3` - AdGuard filtering, YouTube fix, Auto-whitelist engine  
**Status:** 🔄 In Development

---

## 📊 Development Summary

### **Fitur Utama yang Diimplementasikan:**

| Fitur | Status | Deskripsi |
|-------|--------|-----------|
| **AdGuard-Style Filtering** | ✅ Done | Blocklist Google Ads + 50+ Google Services allowlist |
| **TCP Forwarding** | ✅ Done | Forward TCP traffic untuk YouTube & apps lainnya |
| **Enhanced Auto-Whitelist** | ✅ Done | 10 kategori, 100+ keywords, 80-120 apps |
| **Live Activity Logging** | ✅ Done | Real-time DNS query logging dengan app identification |
| **Smart Bypass Engine** | ✅ Done | ISP direct untuk apps kritis (no VPN tunnel) |
| **DNS Trap & Routing** | ✅ Done | DNS interception di port 53 dengan backup DNS |

### **File Baru Ditambahkan:**
```
✅ AppCategory.kt - Kategori aplikasi untuk whitelist
✅ DnsFilterEngine.kt - Engine filtering DNS dengan HashSet
✅ DnsForwarder.kt - Forwarder DNS query ke server
✅ DohBlocker.kt - Blokir DNS over HTTPS untuk mencegah bypass
✅ SmartBypassEngine.kt - Deteksi otomatis apps yang perlu bypass
✅ StatisticsEngine.kt - Tracking statistik traffic
✅ TcpConnectionManager.kt - Manajemen koneksi TCP forwarding
✅ WhitelistManager.kt - Manajemen whitelist pengguna
```

---

## 📋 Testing Checklist Lengkap

### **1. CORE FUNCTIONALITY** 🔴

#### **1.1 VPN Service**
```
□ Install & launch ZeroAd
□ Grant VPN permission
□ Start VPN service
□ Verify VPN icon appears in status bar
□ Check notification shows "ZeroAd Active"
□ Verify VPN runs in foreground
□ Stop VPN - verify clean shutdown
□ Restart VPN - verify reconnection works
```

#### **1.2 Live Activity Logging**
```
□ Start VPN
□ Open Activity tab
□ Verify startup log appears: "ZeroAd Service STARTED"
□ Open YouTube app
□ Check Activity tab shows DNS queries from YouTube
□ Verify app name displayed correctly (not "unknown")
□ Scroll through logs - should be continuous
□ Verify log format: timestamp|domain|category|action|uid
□ Check logs update in real-time
□ Stop VPN - verify logs persist
□ Restart app - verify logs retained (if implemented)
```

#### **1.3 DNS Filtering**
```
□ Start VPN
□ Open browser, visit website with ads
□ Check Activity tab for blocked domains:
  □ googleads.g.doubleclick.net → BLOCKED
  □ pagead2.googlesyndication.com → BLOCKED
  □ adservice.google.com → BLOCKED
□ Verify allowed domains:
  □ play.googleapis.com → ALLOWED
  □ youtube.googleapis.com → ALLOWED
  □ googleapis.com → ALLOWED
□ Count blocked ads per minute (target: 10-50)
□ Verify no false positives on legitimate sites
```

---

### **2. CONNECTIVITY TESTING** 🌐

#### **2.1 YouTube (Critical)**
```
□ Open YouTube with VPN ON
□ Home feed loads within 3 seconds
□ Search for video - results appear
□ Play video at 1080p - smooth playback
□ Skip through video - no buffering
□ Load comments section
□ Try upload video (if applicable)
□ Test YouTube Music (if installed)
□ Test YouTube Kids (if installed)
□ Verify no "No internet connection" errors
```

#### **2.2 Social Media Apps**
```
□ Instagram:
  □ Feed loads
  □ Stories play
  □ Reels play smoothly
  □ DMs send/receive
  □ No connectivity errors

□ TikTok:
  □ Videos load instantly
  □ Scroll through feed smoothly
  □ Upload video (if applicable)
  □ Live stream (if applicable)

□ Twitter/X:
  □ Tweets load
  □ Images/videos display
  □ Post tweets
  □ DMs work

□ Facebook:
  □ Feed loads
  □ Posts display correctly
  □ Images/videos load
  □ Messenger integration works

□ WhatsApp:
  □ Messages send/receive
  □ Images/videos send
  □ Voice notes work
  □ Calls connect (voice/video)
```

#### **2.3 Browser Testing**
```
□ Chrome:
  □ Visit google.com
  □ Visit youtube.com
  □ Visit news sites (with ads)
  □ Verify ads are blocked
  □ Verify content loads normally
  □ Test incognito mode
  □ Test multiple tabs

□ Firefox (if installed):
  □ Same tests as Chrome
  □ Verify DNS over HTTPS is blocked
  □ Test with uBlock Origin (should not conflict)

□ Edge/Opera/Brave:
  □ Basic browsing works
  □ Ads are blocked
  □ No connectivity issues
```

#### **2.4 E-Commerce Apps**
```
□ Shopee:
  □ App opens
  □ Product feed loads
  □ Search works
  □ Product images display
  □ Checkout process works
  □ Payment gateway loads
  □ Order tracking works

□ Tokopedia:
  □ Same tests as Shopee
  □ Verify smooth browsing
  □ Test Tokopedia Play (if exists)

□ Lazada/Bukalapak/Blibli:
  □ Basic browsing works
  □ Images load
  □ Search functions
  □ Cart & checkout work
```

#### **2.5 Banking & Finance** 💳
```
□ BCA Mobile:
  □ Login works
  □ Account balance displays
  □ Transfer works
  □ Bill payment works
  □ Transaction history loads
  □ No connectivity timeouts

□ Mandiri Online:
  □ Same tests as BCA
  □ Verify secure connection

□ Jenius:
  □ Login works
  □ Card management works
  □ Transactions display
  □ Savings goals accessible

□ DANA/OVO/GoPay/ShopeePay:
  □ Balance displays
  □ Transfer works
  □ Payment QR scan works
  □ Transaction history loads
  □ Top-up works

□ PayPal:
  □ Login works
  □ Balance displays
  □ Send/receive money works
  □ Link bank account works
```

#### **2.6 Travel & Booking** ✈️
```
□ Traveloka:
  □ Flight search works
  □ Hotel search works
  □ Xperience booking works
  □ Payment gateway loads
  □ Booking confirmation received

□ Tiket.com:
  □ Same tests as Traveloka
  □ Verify smooth browsing

□ Booking.com/Agoda:
  □ Hotel search works
  □ Filters apply correctly
  □ Images load
  □ Booking process works

□ AirAsia/Garuda/Lion Air:
  □ Flight search works
  □ Seat selection works
  □ Payment works
  □ E-ticket received

□ Grab/Gojek:
  □ Ride booking works
  □ Food delivery works
  □ Mart delivery works
  □ Payment works
  □ Driver tracking works
```

#### **2.7 Food Delivery** 🍔
```
□ GoFood:
  □ Restaurant list loads
  □ Menu displays
  □ Cart works
  □ Payment works
  □ Order tracking works

□ GrabFood/ShopeeFood:
  □ Same tests as GoFood
  □ Verify smooth ordering process
```

#### **2.8 Streaming & Entertainment** 🎬
```
□ Netflix:
  □ Login works
  □ Browse titles
  □ Play video at 1080p
  □ Download for offline (if applicable)
  □ No buffering issues

□ Spotify:
  □ Login works
  □ Browse playlists
  □ Play songs
  □ Download for offline
  □ No connectivity issues

□ Disney+/HBO Go/Prime Video:
  □ Login works
  □ Content browses
  □ Video plays smoothly
  □ Subtitles work
  □ Download works (if applicable)

□ Vidio/iflix/Viu:
  □ Same tests as above
  □ Verify local content works
```

#### **2.9 Investment & Crypto** 📈
```
□ Ajaib:
  □ Login works
  □ Stock prices update
  □ Buy/sell orders work
  □ Portfolio displays
  □ Transaction history loads

□ Stockbit/Bibit/IPOT:
  □ Same tests as Ajaib
  □ Verify real-time data updates

□ Binance/Indodax/Tokocrypto/Pintu:
  □ Login works
  □ Crypto prices update
  □ Buy/sell crypto works
  □ Wallet balance displays
  □ Transfer crypto works
```

#### **2.10 Gaming** 🎮
```
□ Mobile Legends:
  □ Login works
  □ Matchmaking works
  □ Game loads
  □ In-game connection stable
  □ No lag spikes
  □ Match completes successfully

□ PUBG Mobile:
  □ Same tests as ML
  □ Verify voice chat works

□ Genshin Impact:
  □ Login works
  □ Game loads
  □ Open world exploration
  □ Co-op mode works
  □ No disconnections

□ Clash of Clans/Clash Royale:
  □ Login works
  □ Base loads
  □ Attacks work
  □ Clan features work

□ Among Us:
  □ Login works
  □ Matchmaking works
  □ Game runs smoothly
  □ Voice chat works (if applicable)
```

---

### **3. AUTO-WHITELIST TESTING** ✅

#### **3.1 Verify Auto-Detection**
```
□ Install new app (e.g., Shopee)
□ Start VPN
□ Open Activity tab
□ Verify Shopee gets ISP Direct (no filtering icon)
□ Verify no ads blocked for Shopee
□ Check bypass list in settings
□ Confirm Shopee appears in bypass list
□ Verify reason shown: "E-Commerce App"
```

#### **3.2 Category Verification**
```
□ YouTube & Google:
  □ YouTube - 4K streaming works
  □ YouTube Music - plays smoothly
  □ Google Maps - navigation works
  □ Google Photos - backup works
  □ Gmail - send/receive works

□ Browser Apps:
  □ Chrome - full speed browsing
  □ Firefox - no issues
  □ Edge - works normally
  □ Opera - works normally
  □ Brave - works normally

□ E-Commerce:
  □ Shopee - smooth browsing
  □ Tokopedia - smooth browsing
  □ Lazada - works
  □ Bukalapak - works
  □ Blibli - works

□ Finance:
  □ BCA - transfer works
  □ Mandiri - works
  □ BNI - works
  □ BRI - works
  □ DANA - payment works
  □ OVO - payment works

□ Travel:
  □ Traveloka - booking works
  □ Tiket.com - works
  □ Booking.com - works
  □ Agoda - works
  □ AirAsia - booking works

□ Social Media:
  □ Instagram - smooth scrolling
  □ TikTok - videos play
  □ WhatsApp - messages send
  □ Telegram - works
  □ Twitter - feed loads

□ Streaming:
  □ Netflix - no buffering
  □ Spotify - plays smoothly
  □ Disney+ - works
  □ Prime Video - works

□ Investment:
  □ Ajaib - trades execute
  □ Bibit - works
  □ Stockbit - works
  □ Pintu - crypto trades
```

#### **3.3 User Whitelist**
```
□ Go to Activity tab
□ Find an app not auto-whitelisted
□ Long press or tap app
□ Select "Add to Whitelist"
□ Verify app added to whitelist
□ Restart VPN
□ Verify app now bypasses tunnel
□ Check bypass list - app appears
□ Remove from whitelist
□ Restart VPN
□ Verify app now goes through tunnel
```

---

### **4. AD BLOCKING EFFECTIVENESS** 🛡️

#### **4.1 Website Ads**
```
□ Visit news sites:
  □ detik.com - ads blocked?
  □ kompas.com - ads blocked?
  □ tribunnews.com - ads blocked?
  □ cnnindonesia.com - ads blocked?

□ Visit tech sites:
  □ theverge.com - ads blocked?
  □ techcrunch.com - ads blocked?
  □ engadget.com - ads blocked?

□ Visit video sites:
  □ YouTube - video ads blocked?
  □ Vimeo - ads blocked?
```

#### **4.2 In-App Ads**
```
□ Games with ads:
  □ Ads appear before game starts? (should be blocked)
  □ Banner ads visible? (should be blocked)
  □ Interstitial ads? (should be blocked)
  □ Rewarded ads? (should be blocked or work)

□ Utility apps:
  □ Flashlight app - ads blocked?
  □ Calculator app - ads blocked?
  □ File manager - ads blocked?
```

#### **4.3 False Positive Testing**
```
□ Verify legitimate content NOT blocked:
  □ Google login works
  □ Facebook login works
  □ Instagram images load
  □ YouTube videos play
  □ Website content displays
  □ App updates download (Play Store)
  □ In-app purchases work
```

---

### **5. PERFORMANCE TESTING** ⚡

#### **5.1 Speed Tests**
```
□ Speedtest by Ookla:
  □ Download speed (with VPN)
  □ Upload speed (with VPN)
  □ Ping (with VPN)
  □ Compare with VPN off
  □ Acceptable: <20% speed reduction

□ Fast.com:
  □ Load time
  □ Download speed
  □ Compare with VPN off
```

#### **5.2 Battery Impact**
```
□ Full charge to 0% with VPN on
□ Record battery drain per hour
□ Compare with VPN off
□ Acceptable: <10% additional drain per hour
□ Check battery usage in settings
□ ZeroAd should not be top consumer
```

#### **5.3 Memory Usage**
```
□ Check RAM usage in Dev Options:
  □ ZeroAd memory usage (target: <100MB)
  □ No memory leaks after 1 hour
  □ No memory leaks after 4 hours
  □ App doesn't crash under pressure

□ Test with many apps open:
  □ Open 10+ apps
  □ Switch between apps
  □ ZeroAd should not crash
  □ Other apps should not crash
```

#### **5.4 CPU Usage**
```
□ Monitor CPU in Dev Options:
  □ Idle CPU usage (target: <1%)
  □ Active CPU usage (target: <5%)
  □ No CPU spikes during normal use
  □ No thermal throttling
```

---

### **6. EDGE CASES & STRESS TESTING** 🔬

#### **6.1 Network Switching**
```
□ WiFi → Mobile Data:
  □ Switch while VPN active
  □ Verify VPN stays connected
  □ Test internet works immediately
  □ Check Activity tab logs continue

□ Mobile Data → WiFi:
  □ Switch while VPN active
  □ Verify VPN stays connected
  □ Test internet works
  □ No reconnection needed

□ WiFi → Airplane Mode → WiFi:
  □ Enable airplane mode
  □ Wait 10 seconds
  □ Disable airplane mode
  □ Verify VPN reconnects automatically
  □ Test internet works
```

#### **6.2 App Lifecycle**
```
□ Background → Foreground:
  □ Send ZeroAd to background
  □ Wait 1 minute
  □ Return to ZeroAd
  □ VPN should still be active
  □ Logs should still be visible

□ Kill & Restart:
  □ Kill ZeroAd from recents
  □ Verify VPN stops (expected)
  □ Restart ZeroAd
  □ VPN should restart (manual or auto)

□ Screen On/Off:
  □ Turn screen off
  □ Wait 5 minutes
  □ Turn screen on
  □ VPN should still be active
  □ Test internet works
```

#### **6.3 High Traffic Load**
```
□ Open 5 apps simultaneously:
  □ YouTube, Instagram, Chrome, Shopee, WhatsApp
  □ Scroll through all apps rapidly
  □ Check Activity tab - logs should not lag
  □ No crashes or freezes

□ Download large file:
  □ Download 1GB file
  □ Monitor Activity tab
  □ Verify download speed is acceptable
  □ Check for any interruptions

□ Stream 4K video:
  □ Play 4K video on YouTube
  □ Watch for 10 minutes
  □ No buffering (if network supports)
  □ App doesn't overheat
```

#### **6.4 DNS-Specific Tests**
```
□ DNS over HTTPS blocking:
  □ Try to enable DoH in Firefox
  □ Should be blocked by ZeroAd
  □ Check Activity tab for DoH attempts
  □ Verify fallback to system DNS

□ DNS over TLS blocking:
  □ Try to enable DoT in Android settings
  □ Should be blocked or bypassed
  □ Verify DNS still filtered

□ Custom DNS apps:
  □ Install 1.1.1.1 app
  □ Try to activate
  □ Should not conflict with ZeroAd
  □ Verify ZeroAd DNS takes precedence
```

---

### **7. COMPATIBILITY TESTING** 📱

#### **7.1 Android Versions**
```
□ Android 10 (API 29)
□ Android 11 (API 30)
□ Android 12 (API 31)
□ Android 12L (API 32)
□ Android 13 (API 33)
□ Android 14 (API 34)
□ Android 15 (API 35) - if available
```

#### **7.2 Device Manufacturers**
```
□ Samsung (One UI)
□ Xiaomi (MIUI/HyperOS)
□ OPPO (ColorOS)
□ vivo (Funtouch OS)
□ realme (realme UI)
□ OnePlus (OxygenOS)
□ Google Pixel (Stock Android)
□ ASUS (ZenUI)
```

#### **7.3 Screen Sizes**
```
□ Small (<5.5")
□ Medium (5.5"-6.2")
□ Large (>6.2")
□ Foldable (if available)
```

---

### **8. UI/UX TESTING** 🎨

#### **8.1 Navigation**
```
□ All tabs accessible:
  □ Home
  □ Activity
  □ Scanner
  □ Settings
  □ About

□ Bottom navigation works
□ Back button behavior correct
□ Deep links work (if implemented)
□ No UI freezes or jank
```

#### **8.2 Home Screen**
```
□ VPN toggle works
□ Status displays correctly
□ Statistics visible (if implemented)
□ Quick settings accessible
□ No layout issues
```

#### **8.3 Activity Screen**
```
□ Logs display correctly
□ Scroll is smooth (60 FPS)
□ Search/filter works (if implemented)
□ Clear logs button works
□ App icons display (if implemented)
□ Categories color-coded
□ Timestamps readable
```

#### **8.4 Scanner Screen**
```
□ Scan button works
□ Progress indicator displays
□ Results display correctly
□ Risk scores visible
□ App details accessible
□ No crashes during scan
```

#### **8.5 Settings Screen**
```
□ All toggles work
□ Whitelist management works
□ Blocklist update works
□ DNS settings accessible
□ App version visible
□ No layout issues
```

#### **8.6 Dark Mode**
```
□ Dark mode displays correctly
□ Light mode displays correctly
□ System theme follows
□ No contrast issues
□ Text readable in both modes
```

---

### **9. SECURITY TESTING** 🔐

#### **9.1 Privacy**
```
□ Verify no data sent to external servers
□ Check network traffic with Wireshark/Fiddler
□ No analytics SDKs sending data
□ No crash reporting without consent
□ Logs stored locally only
```

#### **9.2 Permissions**
```
□ Request only necessary permissions
□ No dangerous permissions without reason
□ Permission explanations clear
□ Revoke permissions - app handles gracefully
```

#### **9.3 Data Storage**
```
□ No sensitive data in SharedPreferences
□ No logs stored permanently
□ Cache cleared on app uninstall
□ No leftover data after uninstall
```

---

### **10. BUG TRACKING** 🐛

#### **Known Issues to Verify:**
```
□ [ ] TCP forwarding works for all apps
□ [ ] No memory leaks after extended use
□ [ ] Live Activity doesn't lag with 1000+ logs
□ [ ] Auto-whitelist detects all critical apps
□ [ ] No false positives in ad blocking
□ [ ] YouTube works 100% of the time
□ [ ] No battery drain issues
□ [ ] VPN reconnects after network switch
□ [ ] No crashes on low-end devices
□ [ ] DNS over HTTPS properly blocked
```

#### **Regression Testing:**
```
□ Test all previously fixed bugs
□ Verify no reintroduction of old issues
□ Check git history for fixed bugs
□ Run through old test cases
```

---

## 📊 Test Results Template

### **Summary**
```
Total Tests: XXX
Passed: XXX
Failed: XXX
Skipped: XXX
Pass Rate: XX%
```

### **Critical Issues**
| ID | Issue | Severity | Status |
|----|-------|----------|--------|
| 1 | | 🔴 Critical | Open |
| 2 | | 🟠 High | Open |

### **Performance Metrics**
| Metric | Result | Target | Status |
|--------|--------|--------|--------|
| VPN Connect Time | 2.3s | <3s | ✅ |
| Memory Usage | 85MB | <100MB | ✅ |
| Battery Drain | 8%/hr | <10%/hr | ✅ |
| Speed Reduction | 12% | <20% | ✅ |

---

## 🚀 Testing Workflow

### **Quick Test (15 minutes)**
```
1. Install & start VPN (2 min)
2. Test YouTube (3 min)
3. Test Instagram + TikTok (3 min)
4. Test Chrome browsing (2 min)
5. Check Live Activity logs (2 min)
6. Test one banking app (3 min)
```

### **Standard Test (1 hour)**
```
1. Quick test (15 min)
2. Test all social media apps (15 min)
3. Test e-commerce apps (15 min)
4. Test streaming apps (15 min)
```

### **Full Test (4+ hours)**
```
1. Standard test (1 hour)
2. Test all categories (1 hour)
3. Performance testing (1 hour)
4. Edge cases & stress testing (1+ hour)
```

---

## 📝 Notes

- ✅ = Test passed
- ❌ = Test failed (log bug)
- ⚠️ = Test passed with warnings
- 🔄 = Test in progress
- ⏭️ = Test skipped (note reason)

---

**Last Updated:** 1 Maret 2026  
**Developed with ❤️ by initHD3v**
