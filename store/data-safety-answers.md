# Play Console > Data safety - suggested answers
(Check each against Google's current form; the AdMob page "Play Data safety" lists what the Ads SDK collects.)

- Does the app collect or share user data? **Yes** (only because of the Ads SDK and Play Billing).
- Is all data encrypted in transit? **Yes**.
- Can users request data deletion? Your own financial data is local: "Users can delete data by clearing app data / uninstalling". For SDK data, Google's policies apply.
- Data types (collected AND shared with third parties for Advertising): 
  - **Device or other IDs** (Advertising ID): collected, shared, purpose Advertising or marketing, not optional-off? mark as required for ads.
  - **App info and performance** (crash logs/diagnostics from the Ads SDK): collected, shared, purpose Analytics / Fraud prevention.
  - **Approximate location** (IP-derived by the Ads SDK): collected, shared, purpose Advertising.
- **Financial info / Personal info / Photos / Contacts**: NOT collected (everything stays on device).
- Purchases are processed by Google Play; no payment info is received by you.
