# Publishing Expendora on Google Play - step by step

I could not compile or sign anything in my environment, so the signed bundle (.aab) is built by
GitHub Actions from this project. Total effort: about one hour of clicking, plus Google's review time.

## 1. Make your upload key (once)
1. Keep the GitHub repository **private**.
2. GitHub > Actions > **Create upload key** > Run workflow. Choose a password and write it down.
3. Open the finished run, download the artifact `moneta-upload-key`, unzip it.
4. **Store `moneta-upload.jks` and the password in two safe places** (email to yourself + cloud drive). If you lose it, updating the app becomes a support request to Google.
5. Repo > Settings > Secrets and variables > Actions > New secret, add:
   - `RELEASE_KEYSTORE_BASE64` = the whole text inside `moneta-upload.jks.base64.txt`
   - `RELEASE_STORE_PASSWORD` = your password
   - `RELEASE_KEY_PASSWORD` = the same password
   - `RELEASE_KEY_ALIAS` = `upload` (or what you typed)
6. Delete the key artifact from the run afterwards.

## 2. AdMob (free ads account)
1. admob.google.com > create account > Add app > Android > "Not published yet" is fine (link it after launch).
2. Create one ad unit: **Rewarded**. Copy the **App ID** (`ca-app-pub-...~...`) and the **Ad unit ID** (`ca-app-pub-.../...`).
3. Add secrets `ADMOB_APP_ID` and `ADMOB_REWARDED_ID`. Without them the app shows Google's TEST ads (no earnings).
4. In AdMob > Privacy & messaging, create a GDPR message (needed for EU/UK users) and publish it.
5. AdMob pays by bank transfer once you pass the payment threshold (set up payments and tax info there).

## 3. Build the bundle
Actions > **Build APKs** > Run workflow. When done, download artifact **moneta-play-store-aab**.
That `.aab` is what you upload. Each run raises the version code automatically.

## 4. Play Console (play.google.com/console)
1. Create the app: name "Expendora", default language, App, **Free**.
2. **Play App Signing**: accept it. You upload with your upload key; Google signs for users.
3. **Monetize > Products > In-app products > Create product**
   - Product ID exactly `moneta_plus`, name "Expendora Plus", one-time, set your price
     (suggested: about $4.99 / local equivalents; Play lets you set each country), Activate.
4. **Testing**: upload the .aab to **Internal testing** first, add your own Gmail as tester, install from the Play link and test the purchase with a **License tester** account (Settings > License testing - purchases are free for them).
5. **Production readiness** (Dashboard checklist):
   - Privacy policy URL: publish `store/privacy-policy.md` as a web page (GitHub Pages or any free site) and paste the link.
   - App access: all functionality available without login.
   - Ads: **Yes, contains ads**.
   - Content rating questionnaire (finance tracker, no user content: typically "Everyone").
   - Target audience: 18+ (not for children).
   - Data safety form: use `store/data-safety-answers.md`.
   - Financial features declaration: choose "My app doesn't provide financial services"/personal budgeting tracker (it only records the user's own entries).
   - Store listing: text in `store/listing.md`; you must add the 512x512 icon, a 1024x500 feature graphic and at least 2 phone screenshots.
6. New personal developer accounts must usually run a **closed test with at least 12 testers for 14 days** before Production access. Check the current rule in Play Console's dashboard; start it early.
7. Create the production release, upload the .aab, submit for review.

## 5. Things to double check before you press Publish
- Install from the Internal testing link on a real phone: purchase, restore, ad unlock, backup to Drive via the "Download backup" picker.
- The 14-day trial starts only when the user taps "Start free trial" on the Plus screen.
- Price shown on the Plus screen comes from Play, so it is always correct per country.

## Not done in this build (honest list)
- Automatic upload to Google Drive on a schedule needs a Google Cloud OAuth setup tied to your signing key; it is a possible later add-on. Today users save a backup to Drive (or any cloud app) through the file picker, free for you.
- Nothing here was compiled by me; if the first CI build prints an error, send me the log and I will fix it quickly.
