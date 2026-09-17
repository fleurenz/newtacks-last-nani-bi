const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

// 1. You MUST have your serviceAccountKey.json in this same folder!
const serviceAccount = require('./serviceAccountKey.json');

initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore();

const chatbotData = {
  // --- ACCOUNT SAFETY (The new 3-day rule) ---
  account_restoration: {
    role: "ALL",
    keywords: ["restore", "delete", "undo", "bawi", "mabalik", "nabura", "remove account", "sayang", "wa-it", "wait"],
    response: "Changed your mind? If you marked your account for deletion, you have exactly 3 days to restore it! Just log in again with your credentials and tap 'Restore' to get everything back."
  },

  // --- LOCATION & PINNING (For Davao users) ---
  location_pinning: {
    role: "CLIENT",
    keywords: ["pin", "mapa", "lugar", "tultol", "landmark", "asa", "where", "location", "balay", "gate", "subdivision", "dapit"],
    response: "Para dili masaag ang worker, use the 'Pin on Map' button! Move the pin to your exact house or gate. It's much better than just typing the address!"
  },

  // --- WORKER TIERS (The 'Trusted' vs 'Pro' logic) ---
  verification_tiers: {
    role: "WORKER",
    keywords: ["tier", "trusted", "pro", "level", "badges", "points", "ranking", "unverified", "asay mas maayo", "unsaon pag pro"],
    response: "Want to be a 'Verified Pro'? Upload certificates for ALL your selected skills to reach Tier 2. If you only verify half (50%), you'll be Tier 1 (Trusted). Pro workers get hired 3x faster!"
  },

  // --- REQUESTING FOR OTHERS (The new feature) ---
  request_for_others: {
    role: "CLIENT",
    keywords: ["mama", "papa", "barkada", "friend", "uban", "others", "representative", "tita", "tito", "kaila", "someone else"],
    response: "Yes, you can book a fix for someone else! Choose 'Others' in the Request screen, type their name, and tell us if they are your Family, Coworker, or Friend."
  },

  // --- COMPANY HIRING TIPS ---
  company_hiring_tips: {
    role: "COMPANY",
    keywords: ["resume", "applicant", "pili", "choose", "hiring", "interview", "cv", "biodata", "review", "qualified", "background check"],
    response: "When reviewing applicants, check their 'Verified Skills' and 'General Certificates'. If a worker is Tier 2, it means our admins have verified every single one of their skills!"
  },

  // --- EMERGENCY / URGENT FIXES ---
  urgent_fixes: {
    role: "CLIENT",
    keywords: ["urgent", "dali", "leak", "baha", "sunog", "short circuit", "brownout", "sira", "guba", "emergency", "tabang", "help now"],
    response: "For urgent repairs like leaks or electrical issues, set your rate to 'One-time' and offer a competitive amount to attract the nearest available workers quickly!"
  },

  // --- TRUST & SAFETY (Reporting) ---
  safety_etiquette: {
    role: "ALL",
    keywords: ["bastos", "rude", "scam", "atik", "way klaro", "no show", "late", "reklamo", "away", "fight", "stolen", "kawat"],
    response: "STRACT has zero tolerance for bad behavior. If someone is being rude or didn't show up, go to their profile and tap 'Report'. Our admins will review your evidence immediately."
  },

  // --- APP PURPOSE ---
  what_is_stract: {
    role: "ALL",
    keywords: ["unsa ni", "what is this", "kumpanya", "is it free", "bayad ba", "libre", "help me", "about", "stract info"],
    response: "STRACT is Davao's homegrown platform for home services. We connect skilled workers with people who need fixes. Our mission is to provide 'Income for Workers, Ease for Homeowners'!"
  },

  // --- SYSTEM ISSUES ---
  login_trouble: {
    role: "ALL",
    keywords: ["error", "cannot login", "di ko kapaso", "bug", "crash", "stuck", "loading", "wait", "otp", "link"],
    response: "Having trouble? Make sure you are using the Magic Link sent to your Gmail. If the app crashes, check your internet connection or try clearing the app cache."
  },

  // --- 1. THE "HOW DO I START" (ONBOARDING) ---
  onboarding_help: {
    role: "ALL",
    keywords: ["unsaon", "how to", "paano", "start", "gamiton", "tutorial", "guide", "tudlo", "help", "first time", "bag-o"],
    response: "Welcome! If you're a Worker, go to Account to upload your resume and certificates. If you're a Client, just tap 'Request a Fix' on your home screen to find help. STRACT makes it easy!"
  },

  // --- 2. THE "MONEY & PRICING" (DEEP DIVE) ---
  pricing_logic: {
    role: "ALL",
    keywords: ["mahal", "expensive", "barato", "cheap", "discount", "price", "presyo", "negotiate", "hangyo", "ayos", "fixed"],
    response: "Rates are set by the Client, but Workers can see them before accepting. For long jobs, we recommend 'Per Day' or 'Per Hour'. Always agree on the final price via Chat before the work starts!"
  },
  refund_info: {
    role: "CLIENT",
    keywords: ["refund", "balik pera", "bawi", "mali", "wrong payment", "overpay", "uli", "kwarta", "cancel pay"],
    response: "Since STRACT uses direct payment (COD/GCash), refunds must be discussed directly with the worker. If there's a dispute, tap 'Report' and our admins will help mediate the situation."
  },

  // --- 3. THE "TRUST & SECURITY" (EMERGENCY) ---
  theft_safety: {
    role: "ALL",
    keywords: ["kawat", "nawala", "stolen", "stole", "robbed", "looting", "missing", "security", "safe", "pulis", "police"],
    response: "Safety is our priority. Every worker is verified with a Resume and IDs. If something goes missing, Report the user immediately via their profile and contact local authorities. We will provide all log data to the police."
  },
  scam_protection: {
    role: "ALL",
    keywords: ["scam", "atik", "fake", "bakak", "liar", "manliloko", "ilad", "modus", "hacker"],
    response: "Don't get 'ilad'! Never pay a worker before they arrive at your house. Only confirm payment once the job is actually finished and you are happy with the work."
  },

  // --- 4. THE "TECHNICAL TROUBLESHOOTER" ---
  gps_trouble: {
    role: "ALL",
    keywords: ["gps", "map", "location", "wrong address", "layo", "mali", "saag", "lost", "cannot find", "tultul", "loading map"],
    response: "Is the map acting up? Make sure your phone's GPS is ON and set to 'High Accuracy'. If your house is hard to find, use the 'Pin on Map' feature to show the exact spot!"
  },
  app_bugs: {
    role: "ALL",
    keywords: ["bug", "error", "crash", "stuck", "hangu", "loading", "bagal", "slow", "white screen", "cannot click"],
    response: "Sorry for the glitch! Try closing the app completely and reopening it. If it persists, clear the 'App Cache' in your phone settings or check if you have the latest update from the Play Store."
  },

  // --- 5. THE "WORKER HUSTLE" (PRO TIPS) ---
  more_jobs_tip: {
    role: "WORKER",
    keywords: ["walay jobs", "no work", "mingaw", "hilom", "laay", "unsaon pag daghan", "more income", "raket", "tips"],
    response: "Want more raket? (1) Reach Tier 2 by verifying all skills. (2) Ask clients for a 5-star rating. (3) Keep your 'About Me' professional. Pro workers with good reviews always get picked first!"
  },
  rejection_info: {
    role: "WORKER",
    keywords: ["rejected", "wala nadawat", "cancel", "failed application", "ngano", "why", "sad", "refused"],
    response: "Don't lose hope! Clients or Companies might pick someone else based on experience or location. Keep your profile updated and apply for 'Open' positions in the Feed."
  },

  // --- 6. THE "PROFESSIONAL/COMPANY" (HR) ---
  bulk_hiring: {
    role: "COMPANY",
    keywords: ["pakyaw", "many workers", "team", "group", "construction", "project", "daghan", "staff", "crew"],
    response: "For big projects, use 'Employment Type: Full-time' in your Hiring Post. You can hire multiple workers for one post by increasing the 'Vacancies' count!"
  },

  // --- 7. DAVAO LOCAL CONTEXT (BISAYA/SLANG) ---
  davao_slang: {
    role: "ALL",
    keywords: ["pila", "tag-pila", "wer na u", "dali lang", "dugay", "kadyot", "wait lang", "balay", "lugar", "asa"],
    response: "Tey understands you! For 'Pila' (Price), check the job details. For 'Asa' (Location), check the Map. For 'Dali lang' (Urgent), please use the Chat to tell the other user!"
  },

  // --- 8. THE "WHAT IF I'M LATE" ---
  timing_etiquette: {
    role: "ALL",
    keywords: ["late", "dugay", "waiting", "absent", "no show", "wala niabot", "wala diri", "trapik", "traffic"],
    response: "Davao traffic can be tough! Always check the 'Worker is on the way' status on your map. If they are very late without a message, you can cancel the request or Report them."
  },

  // --- 1. THE "WHO BUYS THE TOOLS?" (MATERIALS) ---
  material_dispute: {
    role: "ALL",
    keywords: ["tools", "gamit", "materyales", "materials", "semento", "pintura", "hardware", "buy", "palit", "kandado", "pako", "screw", "kahoy"],
    response: "Usually, the Client provides the materials (like paint or cement), and the Worker brings the tools. However, this is NOT fixed. Please use the Chat to agree on who will buy the materials BEFORE the worker arrives!"
  },

  // --- 2. THE "JOB IS BIGGER THAN I THOUGHT" (SCOPE) ---
  scope_creep: {
    role: "ALL",
    keywords: ["additional", "dugang", "punu", "extra work", "not in the list", "apil ba ni", "plus", "bigger job", "daghan pa"],
    response: "If the job needs more work than originally requested, you can negotiate a 'Dugang' (Extra) fee with the worker. If it's a huge change, we recommend completing the current request and creating a new one for the extra task."
  },

  // --- 3. CANCELLATION POLICIES (THE 'WAY KLARO' RULE) ---
  cancellation_rules: {
    role: "ALL",
    keywords: ["cancel", "change mind", "ayaw na", "dili na dayun", "stop", "back out", "backout", "withdraw", "delete request"],
    response: "You can cancel a request for free if a worker hasn't started heading to your location. If a worker is already 'On the Way', please be considerate and inform them via Chat. Frequent cancellations may result in account warnings!"
  },

  // --- 4. THE "I'M NOT GETTING NOTIFICATIONS" ---
  notification_issues: {
    role: "ALL",
    keywords: ["notif", "wala may tingog", "silent", "didn't hear", "no alert", "missed call", "alarm", "ringing", "not seeing", "not showing"],
    response: "STRACT needs 'Post Notifications' permission to work! Check your Phone Settings > Apps > STRACT > Notifications. Ensure 'Allow Notifications' is ON so you don't miss new jobs or messages."
  },

  // --- 5. CHAT ETIQUETTE & PRIVACY ---
  chat_etiquette: {
    role: "ALL",
    keywords: ["chat", "message", "story", "talk", "storiya", "istorya", "txt", "text", "private", "personal", "number", "viber", "messenger"],
    response: "For your safety, always keep job discussions inside the STRACT Chat. This allows our admins to review the conversation if there is a dispute. Never share your private social media links with strangers!"
  },

  // --- 6. PROHIBITED TASKS (SAFETY FIRST) ---
  prohibited_tasks: {
    role: "ALL",
    keywords: ["illegal", "dangerous", "bawal", "drugs", "weapons", "delikado", "tall building", "electrical live", "high voltage", "threat"],
    response: "Workers have the right to refuse tasks that are dangerous or illegal. If a client asks for something outside the app's professional scope, workers should Report the user immediately."
  },

  // --- 7. AFTER-SERVICE (THE CLEANUP) ---
  cleanup_expectation: {
    role: "CLIENT",
    keywords: ["clean", "hugaw", "messy", "lata", "basura", "garbage", "clutter", "leftover", "hipos", "limpyo"],
    response: "A professional STRACT worker is expected to 'Hipos' (clean up) the immediate work area after finishing. If a worker leaves a big mess, you can mention this in your Review to help other clients!"
  },

  // --- 8. THE "I NEED A RECEIPT FOR MY BOSS" ---
  official_receipts: {
    role: "ALL",
    keywords: ["receipt", "resibo", "proof of pay", "billing", "invoice", "history", "document", "vat", "tax"],
    response: "Every finished job generates an 'Official STRACT Receipt' automatically! You can find all your past receipts in the History tab. You can even download them as images for your personal records."
  },

  // --- 9. WORKING HOURS (DAVAO TIME) ---
  working_hours: {
    role: "ALL",
    keywords: ["night", "gabi", "gabie", "overtime", "ot", "weekend", "sunday", "holiday", "oras", "hours", "time", "pila ka oras"],
    response: "STRACT is 24/7, but most workers prefer 8 AM to 5 PM. If you need a 'Graveyard' shift or weekend fix, set a higher rate and use the Chat to confirm the worker's availability for those hours."
  },

  // --- 10. REVIEWS & RATINGS (THE 'STAR' SYSTEM) ---
  review_logic: {
    role: "ALL",
    keywords: ["star", "rating", "feedback", "comment", "review", "praise", "good work", "bad work", "recommends", "recomenda"],
    response: "Ratings are the heart of STRACT! After a job, please give a Star rating. Good ratings help workers get more 'Raket', and detailed feedback helps clients pick the best skillers in Davao."
  }
}

async function seedDatabase() {
  console.log("🚀 Starting Tey Chatbot bulk upload...");

  for (const [docId, data] of Object.entries(chatbotData)) {
    try {
      // ✅ This targets the correct 'chatbot_knowledge' collection
      await db.collection('chatbot_knowledge').doc(docId).set(data);
      console.log(`✅ Successfully uploaded: ${docId}`);
    } catch (error) {
      console.error(`❌ Error uploading ${docId}:`, error);
    }
  }

  console.log("\n🎉 Bulk upload complete! Tey is now fully trained.");
}

seedDatabase();