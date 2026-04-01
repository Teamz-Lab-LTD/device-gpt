# SEO & Landing Page Audit Prompt (Reusable)

Copy-paste this entire block into Cursor (or any AI) when you need a full audit of a mobile app for SEO and landing page content. Use as-is for any mobile app codebase.

---

You are auditing a mobile app codebase to extract EVERY user-facing feature, string, and marketable element so nothing is missed for SEO and landing page content.

**Scope – extract and list ALL of the following. Do not skip anything. Treat "small" or "obvious" features as equally important; they may be high-value keywords.**

1. **User-facing strings / copy**
   - All localized strings (strings.xml, Localizable.strings, .arb, i18n, translations, or any file containing user-visible text).
   - Screen titles, buttons, labels, placeholders, error messages, tooltips, onboarding text, empty states, success messages.
   - If strings are in code, search for: "title", "label", "message", "description", "hint", "text", "content", "heading", "subtitle", "caption", "placeholder", "error", "success", "empty".

2. **Screens / flows / navigation**
   - Every screen, screen group, tab, bottom nav item, drawer item, modal, or major flow (e.g. onboarding, settings, create habit, view stats).
   - Route names, destination names, deep links if any.
   - List each as: [Screen/Flow name] – [one-line purpose].

3. **Features and capabilities (from code and docs)**
   - Feature flags, feature toggles, "enableX", "showY", or config that gates functionality.
   - Modules, packages, or folders that clearly represent a feature (e.g. "widgets", "backup", "reminders", "analytics", "export").
   - README, CHANGELOG, release notes, in-code comments that describe what the app does or what a feature does.
   - Permissions (camera, storage, notifications, etc.) – each can be a feature/FAQ angle (e.g. "Works offline", "No account required").

4. **Settings and options**
   - Every setting, toggle, option, or preference the user can change (themes, language, notifications, backup, sync, privacy, etc.).
   - Each is a potential feature block or FAQ ("Can I use dark mode?", "Is my data synced?").

5. **Technical differentiators that can be marketed**
   - Offline support, sync, encryption, local-only data, no ads, no account, widget, wear OS / Watch, accessibility, RTL, multi-language, export format, backup/restore, etc.
   - Even if not "visible" in UI, if the code does it, list it – it can be a unique SEO angle.

**Output format (copy-paste friendly):**
- **Strings / copy:** [Bullet list: source file/location → key or line → exact or summarized text.]
- **Screens & flows:** [Numbered list: Screen/flow name – purpose.]
- **Features & capabilities:** [Bullet list: Feature name – one-line description; include source if from flags/docs/comments.]
- **Settings & options:** [Bullet list: Setting/option – what it does.]
- **Technical differentiators:** [Bullet list: Capability – one-line marketing angle.]
- **Raw keyword seeds (for SEO):** [Deduplicated list of short phrases derived from the above: feature names, screen names, action verbs, user goals – e.g. "daily reminders", "dark mode", "export data", "no account", "offline".]

Do not summarize away detail. Include every screen, every major string group, every feature you can infer. If in doubt, include it. The goal is zero missed items for marketing and SEO.
