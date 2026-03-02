# 🎯 ZeroAd Focused Testing - 7 Priority Apps

**Tanggal:** 1 Maret 2026  
**Device:** Xiaomi Pad 5 (Android 13)  
**Version:** 1.2.0+3  
**Commit:** 6f28ec3

---

## 📋 Apps to Test

| # | App | Category | Priority |
|---|-----|----------|----------|
| 1 | **YouTube** | Streaming | 🔴 Critical |
| 2 | **Shopee** | E-Commerce | 🔴 Critical |
| 3 | **Tokopedia** | E-Commerce | 🔴 Critical |
| 4 | **TikTok** | Social Media | 🔴 Critical |
| 5 | **Facebook** | Social Media | 🟠 High |
| 6 | **TCO (The Crew Online)** | Racing Game | 🟠 High |
| 7 | **Pure Sniper** | FPS Game | 🟠 High |

---

## 🧪 Test Scenarios

### **1. YouTube** 🎬 (CRITICAL)

#### Package: `com.google.android.youtube`
#### Expected: ✅ ISP Direct (Auto-whitelist - YouTube Category)

```
□ VPN OFF - Baseline Test
  □ Open YouTube
  □ Home feed loads (<3s)
  □ Search "test video"
  □ Play video at 1080p
  □ Skip through video (no buffering)
  □ Load comments
  □ Note: Normal performance

□ VPN ON - Test
  ✅ Start ZeroAd VPN
  ✅ Verify VPN icon appears
  ✅ Open Activity tab - see "ZeroAd Service STARTED"
  ✅ Open YouTube
  ✅ Home feed loads (<3s)
  ✅ Search "test video"
  ✅ Play video at 1080p
  ✅ Skip through video (no buffering)
  ✅ Load comments
  ✅ Try 4K playback (if available)
  □ Check Activity tab:
    □ See DNS queries from YouTube
    □ youtube.googleapis.com → ALLOWED
    □ play.googleapis.com → ALLOWED
    □ googlevideo.com → ALLOWED
    □ Double-check: No "unknown" app name

□ Ad Blocking Verification
  □ Watch video with pre-roll ads normally
  □ Verify: Ads should be BLOCKED
  □ Check Activity tab for blocked domains:
    □ googleads.g.doubleclick.net → BLOCKED
    □ pagead2.googlesyndication.com → BLOCKED
    □ adservice.google.com → BLOCKED

□ Result:
  Status: ✅ PASS / ⬜ FAIL
  Notes: youtube berjalan dengan baik tanpa ada kendala saat sheild diaktifkan, pada aktifity live youtube tidak ditampilkan pada trafic. ini sudah benar larema youtube sudah mendapatkan isp murni.
  Issues: No issue.
```

---

### **2. Shopee** 🛒 (CRITICAL)

#### Package: `com.shopee.id`
#### Expected: ✅ ISP Direct (Auto-whitelist - E-Commerce Category)

```
□ VPN OFF - Baseline Test
  □ Open Shopee
  □ Home feed loads with banners
  □ Search "test product"
  □ Browse product listings
  □ Open product detail
  □ View product images
  □ Note: Normal performance

□ VPN ON - Test
  ✅ Start ZeroAd VPN
  ✅ Open Activity tab - verify startup log
  ✅ Open Shopee
  ✅ Home feed loads with banners (<3s)
  ✅ Search "test product"
  ✅ Browse product listings (scroll smoothly)
  ✅ Open product detail
  ✅ View product images (all load)
  ✅ Add to cart
  ✅ Proceed to checkout (don't complete)
  □ Check Activity tab:
    □ See DNS queries from Shopee
    ✓ Verify app name = "Shopee" (not unknown)
    ✓ Verify bypassed (no filtering icon)

□ Ad Blocking Verification
  □ Check if Shopee internal ads are blocked
  □ Note: Some internal ads may still show
  □ External ad trackers should be blocked

□ Result:
  Status: ✅ PASS / ⬜ FAIL
  Notes:  shope sudah berjalan dengan baik dan lancar saat shield on, pada trafic live actifity shope tidak di tampilkan ini sudah benar karena shoope sudah mendapatkan isp murni.
  Issues: No issue.

---

### **3. Tokopedia** 🛒 (CRITICAL)

#### Package: `com.tokopedia.tkpd`
#### Expected: ✅ ISP Direct (Auto-whitelist - E-Commerce Category)

```
□ VPN OFF - Baseline Test
  □ Open Tokopedia
  □ Home feed loads
  □ Search "test product"
  □ Browse products
  □ View product images
  □ Note: Normal performance

□ VPN ON - Test
  □ Start ZeroAd VPN
  □ Open Tokopedia
  □ Home feed loads (<3s)
  □ Search "test product"
  □ Browse products (smooth scroll)
  □ View product images (all load)
  □ Add to cart
  □ Proceed to checkout (don't complete)
  □ Check Activity tab:
    □ See DNS queries from Tokopedia
    ✓ Verify app name = "Tokopedia"
    ✓ Verify bypassed

□ Ad Blocking Verification
  □ Check Tokopedia ads
  □ External trackers should be blocked

□ Result:
  Status: ⬜ PASS / ✅ FAIL
  Notes:  Tokopedia tidak dapat memuat konten dengan baik (lag/macet), tokopedia tidak di tampilkan pada live actifity sehingga aku tidak dapat melihat apakah ada dns yang terblokir.
  Issues: _______________________________
```

---

### **4. TikTok** 📱 (CRITICAL)

#### Package: `com.zhiliaoapp.musically`
#### Expected: ✅ ISP Direct (Auto-whitelist - Social Media Category)

```
□ VPN OFF - Baseline Test
  □ Open TikTok
  □ For You feed loads
  □ Scroll through 10 videos
  □ All videos play smoothly
  □ No buffering
  □ Note: Normal performance

□ VPN ON - Test
  □ Start ZeroAd VPN
  □ Open TikTok
  □ For You feed loads (<3s)
  □ Scroll through 10 videos
  □ All videos play smoothly (no buffering)
  □ Videos load instantly on scroll
  □ Like a video
  □ Comment (optional)
  □ Share (optional)
  □ Check Activity tab:
    □ See DNS queries from TikTok
    ✓ Verify app name = "TikTok"
    ✓ Verify bypassed

□ Ad Blocking Verification
  □ Watch for sponsored content
  □ Note: TikTok in-feed ads may still show
  □ External ad trackers should be blocked

□ Result:
  Status: ⬜ PASS / ✅ FAIL
  Notes: Tiktok tidak dapat memuat konten dengan baik (lag/macet), tokopedia tidak di tampilkan pada live actifity sehingga aku tidak dapat melihat apakah ada dns yang terblokir
  Issues: no internet connection
```

---

### **5. Facebook** 📘 (HIGH)

#### Package: `com.facebook.katana`
#### Expected: ✅ ISP Direct (Auto-whitelist - Social Media Category)

```
□ VPN OFF - Baseline Test
  □ Open Facebook
  □ Feed loads
  □ Scroll through posts
  □ Images/videos load
  □ Note: Normal performance

□ VPN ON - Test
  □ Start ZeroAd VPN
  □ Open Facebook
  □ Feed loads (<3s)
  □ Scroll through posts (smooth)
  □ Images load properly
  □ Videos autoplay (if enabled)
  □ Post a status (optional)
  □ Check notifications
  □ Check Activity tab:
    □ See DNS queries from Facebook
    ✓ Verify app name = "Facebook"
    ✓ Verify bypassed

□ Ad Blocking Verification
  □ Check if Facebook ads are blocked
  □ Note: Facebook internal ads may still show
  □ External ad trackers should be blocked

□ Result:
  Status: ✅ PASS /  FAIL
  Notes: _______________________________
  Issues: _______________________________
```

---

### **6. TCO (Tuning Club Online)** 🏎️ (HIGH)

#### Package: `com.twoheadedshark.net`
#### Expected: ⚠️ May need manual whitelist (Game Category)

```
□ Find Package Name
  □ Open Activity tab
  □ Start TCO game
  □ Check Activity tab for game's DNS queries
  □ Note package name: ___________________

□ VPN OFF - Baseline Test
  □ Open TCO
  □ Login works
  □ Garage loads
  □ Multiplayer matchmaking works
  □ Race loads
  □ No connectivity issues
  □ Note: Normal performance

□ VPN ON - Test
  □ Start ZeroAd VPN
  □ Open TCO
  □ Login works
  □ Garage loads (<5s)
  □ Multiplayer matchmaking works
  □ Race loads
  □ In-game connection stable during race
  □ No lag spikes
  □ Race completes successfully
  □ Check Activity tab:
    □ See DNS queries from TCO
    ✓ Verify app name displayed
    ✓ Check if bypassed or filtered

□ If Connection Issues:
  □ Add TCO to manual whitelist
  □ Restart VPN
  □ Retest game
  □ Verify connection improves

□ Result:
  Status: ⬜ PASS / ✅ FAIL
  Notes: Tidak ada log pada live actifity
  Issues: server unavailable, check internet conncetion
  Package Name: com.twoheadedshark.net
  Whitelist Needed: ✅ Yes / ⬜ No
```

---

### **7. Pure Sniper** 🎯 (HIGH)

#### Package: `com.glu.deerhunt2` (verify actual package)
#### Expected: ⚠️ May need manual whitelist (Game Category)

```
□ Find Package Name
  □ Open Activity tab
  □ Start Pure Sniper
  □ Check Activity tab for game's DNS queries
  □ Note package name: ___________________

□ VPN OFF - Baseline Test
  □ Open Pure Sniper
  □ Login works
  □ Mission loads
  □ Gameplay smooth
  □ No connectivity issues
  □ Note: Normal performance

□ VPN ON - Test
  □ Start ZeroAd VPN
  □ Open Pure Sniper
  □ Login works
  □ Mission loads (<5s)
  □ Gameplay smooth (no lag)
  □ In-game purchases load (if applicable)
  □ Ads between missions (if any)
  □ Check Activity tab:
    □ See DNS queries from Pure Sniper
    ✓ Verify app name displayed
    ✓ Check if bypassed or filtered

□ Ad Blocking Verification
  □ Check if interstitial ads are blocked
  □ Check if video ads are blocked
  □ Note: Game may still function without ads

□ If Connection Issues:
  □ Add Pure Sniper to manual whitelist
  □ Restart VPN
  □ Retest game
  □ Verify connection improves

□ Result:
  Status: ⬜ PASS / ⬜ FAIL
  Notes:  pure sniper game berhasil di load dan dapat di mainkan dengan lancar iklan juga terblokir namun IAP tidak berfungsi
  Issues: IAP tidak berfungsi
  Package Name: _________________________
  Whitelist Needed: ✅ Yes / ⬜ No
```

---

## 📊 Summary Sheet

### Quick Results

| App | VPN Works | Auto-Whitelist | Ads Blocked | Performance | Status |
|-----|-----------|----------------|-------------|-------------|--------|
| YouTube | ✅ | ✅ | ✅ | ✅ | ✅ |
| Shopee | ✅ | ✅ | ✅ | ✅ | ✅ |
| Tokopedia ❌| ❌ | ❌ | ❌ | ❌ | ❌ |
| TikTok | ❌ | ❌ | ❌ | ❌ | ❌ |
| Facebook | ✅ | ✅ | ✅ | ✅ | ✅ |
| TCO | ❌ | ❌ | ❌ | ❌ |❌ |
| Pure Sniper | ✅ | ❌ | ✅ | ✅ | ⚠️ |

### Issues Found

| # | App | Issue | Severity | Notes |
|---|-----|-------|----------|-------|
| 1 | | | 🔴/🟠/🟡 | |
| 2 | | | 🔴/🟠/🟡 | |
| 3 | | | 🔴/🟠/🟡 | |

### Auto-Whitelist Verification

| Category | Apps Tested | Working | Notes |
|----------|-------------|---------|-------|
| YouTube/Google | YouTube | ⬜ | |
| E-Commerce | Shopee, Tokopedia | ⬜ | |
| Social Media | TikTok, Facebook | ⬜ | |
| Games | TCO, Pure Sniper | ⬜ | May need manual |

---

## 🎯 Test Workflow

### Phase 1: Setup (5 min)
```
□ Charge device to >50%
□ Close all background apps
□ Clear ZeroAd data (Settings → Apps → ZeroAd → Clear Data)
□ Install/update all 7 apps from Play Store
□ Open each app once to ensure they're installed
```

### Phase 2: Baseline Testing - VPN OFF (15 min)
```
□ Test each app with VPN OFF (2 min each)
□ Note normal performance
□ Check for any pre-existing issues
```

### Phase 3: VPN Testing - VPN ON (30 min)
```
□ Start ZeroAd VPN
□ Test each app with VPN ON (4 min each)
□ Check Activity tab after each app
□ Note any issues
```

### Phase 4: Ad Blocking Verification (10 min)
```
□ Review Activity tab logs
□ Check blocked domains
□ Verify legitimate content not blocked
```

### Phase 5: Summary & Bug Reporting (10 min)
```
□ Fill summary sheet
□ Document all issues
□ Take screenshots if needed
□ Check logcat for errors
```

**Total Time:** ~70 minutes

---

## 🔍 Debug Commands

### Check if app is bypassed:
```bash
adb -s 192.168.100.220:38999 shell dumpsys connectivity | grep -i vpn
```

### Monitor DNS queries in real-time:
```bash
adb -s 192.168.100.220:38999 logcat | grep -i "zeroad\|dns"
```

### Check app package name:
```bash
adb -s 192.168.100.220:38999 shell cmd package list packages | grep -i "shopee\|tokopedia\|tiktok\|twoheadedshark"
```

### Force stop app:
```bash
adb -s 192.168.100.220:38999 shell am force-stop com.shopee.id
```

### Clear app cache:
```bash
adb -s 192.168.100.220:38999 shell pm clear com.shopee.id
```

---

## ✅ Pass Criteria

### Critical Apps (YouTube, Shopee, Tokopedia, TikTok):
- ✅ App opens successfully with VPN ON
- ✅ Core functionality works (browse, search, view)
- ✅ No connectivity errors
- ✅ Auto-whitelist working (bypassed from tunnel)
- ✅ App name displayed correctly in Activity tab

### High Priority Apps (Facebook, TCO, Pure Sniper):
- ✅ App opens successfully with VPN ON
- ✅ Core functionality works
- ✅ No major connectivity issues
- ✅ Gameplay stable (for games)

### Ad Blocking:
- ✅ External ad trackers blocked
- ✅ Legitimate content not affected
- ✅ No false positives

---

**Good luck with testing! 🚀**

*Fill in the checkboxes and notes as you test each app.*
